/*
 * Copyright 2021 Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package analyzer

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.ToposoidUtils
import com.ideal.linked.toposoid.knowledgebase.regist.model.PropositionRelation
import com.ideal.linked.toposoid.protocol.model.base.DeductionResult
import com.ideal.linked.toposoid.protocol.model.frontend.{AnalyzedEdge, AnalyzedEdges, AnalyzedNode}
import com.ideal.linked.toposoid.protocol.model.sat.{FlattenedKnowledgeTree, FormulaSet, SatSolverResult}
import controllers.SatInput
import play.api.libs.json.Json

class ResultAnalyzer {

  /**
   * This function determines if the proposition is trivial.
   * @param flattenKnowledgeTree
   * @return
   */
  def isTrivialProposition(formulaSet:FormulaSet) :Boolean = {
    val formulaElements = formulaSet.subFormulaMap.head._2.split(" ")
    if(formulaElements.size <= 1) return true
    val nonTrivialElements =  formulaSet.subFormulaMap.foldLeft(List.empty[String]){
      (acc, x) => {
        acc ++ x._2.split(" ").filterNot(y => y.equals("AND") || y.equals("OR") || y.equals("IMP") || y.equals("true") || y.equals("false")).toList
      }
    }
    if(nonTrivialElements.size == 0) return true
    false
  }

  /**
   *
   * @param regulation
   * @param hypothesis
   * @return
   */
  def getAnalyzedEdges(regulation:SatInput, hypothesis:SatInput): AnalyzedEdges ={
    isTrivialProposition(regulation.formulaSet) match {
      case true => {
        val analyzedNodes:Map[String, AnalyzedNode] = regulation.parsedKnowledgeTree.satIdMap.keys.foldLeft(Map.empty[String, AnalyzedNode]){
          (acc, x) => acc ++ Map(x -> makeAnalyzedNode(x, false, regulation.trivialIdMap, regulation.parsedKnowledgeTree.sentenceMap, regulation.parsedKnowledgeTree.satIdMap, usedSat=false))
        }
        AnalyzedEdges(makeAnalyzedEdges(regulation.parsedKnowledgeTree.relations, analyzedNodes))
      }
      case _ => {
        val flattenKnowledgeTree = FlattenedKnowledgeTree(regulation.formulaSet, hypothesis.formulaSet)
        val flattenKnowledgeTreeJson:String = Json.toJson(flattenKnowledgeTree).toString()
        val satSolverResultJson:String = ToposoidUtils.callComponent(flattenKnowledgeTreeJson, conf.getString("TOPOSOID_SAT_SOLVER_WEB_HOST"), "9009", "execute")
        val satSolverResult:SatSolverResult = Json.parse(satSolverResultJson).as[SatSolverResult]

        val satIdMapUnion:Map[String, String] = regulation.parsedKnowledgeTree.satIdMap ++ hypothesis.parsedKnowledgeTree.satIdMap
        val trivialIdMapUnion:Map[String, Option[DeductionResult]] = regulation.trivialIdMap ++ hypothesis.trivialIdMap
        val sentenceMapUnion:Map[String, String] =  regulation.parsedKnowledgeTree.sentenceMap ++ hypothesis.parsedKnowledgeTree.sentenceMap
        val relationUnion:List[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])] = regulation.parsedKnowledgeTree.relations ++ hypothesis.parsedKnowledgeTree.relations
        //Removal of dummy variables
        val effectiveSstSolverResult:Map[String, Boolean] = satSolverResult.satResultMap.filter(x => x._1.toInt <= satIdMapUnion.keys.map(_.toInt).max.toInt)
        //satSolverResultが空だったら、Unsatisfied、空でなかったら、Optinum Found
        val analyzedNodes:Map[String, AnalyzedNode] = effectiveSstSolverResult.foldLeft(Map.empty[String, AnalyzedNode]){
          (acc, x) => acc ++  Map(x._1 -> makeAnalyzedNode(x._1, x._2, trivialIdMapUnion, sentenceMapUnion, satIdMapUnion))
        }
        AnalyzedEdges(makeAnalyzedEdges(relationUnion, analyzedNodes))
      }
    }
  }

  /**
   *
   * @param relations
   * @param analyzedNodeMap
   * @return
   */
  def makeAnalyzedEdges(relations:List[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])], analyzedNodeMap:Map[String, AnalyzedNode]): List[AnalyzedEdge] ={
    relations.foldLeft(List.empty[AnalyzedEdge]){
      (acc, x) => {
        val premiseSatIds = x._1
        val premiseRelations = x._2
        val claimSatIds = x._3
        val claimRelations = x._4
        val otherSatIds = x._5
        val otherRelations = x._6

        val analyzedEdgesPremise:List[AnalyzedEdge] =  premiseRelations.size match{
          case 0 => premiseSatIds.size match{
              //If there is only one premise, care is taken because relation is zero
              case 1 => List(AnalyzedEdge(analyzedNodeMap.get(premiseSatIds.head).get, AnalyzedNode("", false, List.empty[String], ""), ""))
              case _ => List.empty[AnalyzedEdge]
            }
          case _ => premiseRelations.map(p1 => AnalyzedEdge(analyzedNodeMap.get(premiseSatIds(p1.sourceIndex)).get, analyzedNodeMap.get(premiseSatIds(p1.destinationIndex)).get, p1.operator  ) )
        }
        val analyzedEdgesClaim:List[AnalyzedEdge] =claimRelations.size match {
          case 0 => claimSatIds.size match{
              //If there is only one claim, care is taken because relation is zero
              case 1 => List(AnalyzedEdge(analyzedNodeMap.get(claimSatIds.head).get, AnalyzedNode("", false, List.empty[String], ""), ""))
              case _ => List.empty[AnalyzedEdge]
            }
          case _  => claimRelations.map(c1 => AnalyzedEdge(analyzedNodeMap.get(claimSatIds(c1.sourceIndex)).get, analyzedNodeMap.get(claimSatIds(c1.destinationIndex)).get, c1.operator  ) )
        }
        val analyzedEdgesOther:List[AnalyzedEdge] =otherRelations.size match {
          case 0 => List.empty[AnalyzedEdge]
          case _ => otherRelations.map(o1 => AnalyzedEdge(analyzedNodeMap.get(otherSatIds(o1.sourceIndex)).get, analyzedNodeMap.get(otherSatIds(o1.destinationIndex)).get, o1.operator  ) )
        }

        val premiseRepId = premiseSatIds.size match {
          case 0 => "-1"
          case _ => premiseSatIds.map(_.toInt).min.toString
        }
        val claimRepId = claimSatIds.size match {
          case 0 => "-1"
          case _ => claimSatIds.map(_.toInt).min.toString
        }

        //Added the relationship between assumptions and claims
        if(!premiseRepId.equals("-1")  && !claimRepId.equals("-1")){
          //both premises and claims
          acc ++ List(AnalyzedEdge(analyzedNodeMap.get(premiseRepId).get, analyzedNodeMap.get(claimRepId).get, "IMP")) ++  analyzedEdgesPremise ++ analyzedEdgesClaim ++ analyzedEdgesOther
        } else if(premiseRepId.equals("-1")  && !claimRepId.equals("-1")){
          //only claims
          acc ++ analyzedEdgesClaim ++ analyzedEdgesOther
        } else{
          acc ++ analyzedEdgesOther
        }
      }
    }
  }

  /**
   *
   * @param satId
   * @param satResult
   * @param trivialPropositionIds
   * @param sentenceMap
   * @param satIdMap
   * @return
   */
  def makeAnalyzedNode(satId:String, satResult:Boolean, trivialPropositionIds:Map[String, Option[DeductionResult]], sentenceMap:Map[String, String], satIdMap:Map[String,String], usedSat:Boolean = true) : AnalyzedNode ={

    val propositionId = satIdMap.get(satId).get
    val sentence:String = sentenceMap.get(propositionId).get
    val reasons:List[String] = List.empty[String]
    val status:String = usedSat match {
      case true => {
        trivialPropositionIds.keys.filter(_ == propositionId).size match {
          case 0 => "OPTIMUM FOUND"
          case _ => "TRIVIAL"
        }
      }
      case _ => {
        trivialPropositionIds.keys.filter(_ == propositionId).size match {
          case 0 => "UNREASONABLE"
          case _ => "TRIVIAL"
        }
      }
    }
    val finalResult:Boolean = status match {
      case "TRIVIAL" =>  trivialPropositionIds.get(propositionId).get.get.status
      case _ => satResult
    }
    AnalyzedNode(sentence, finalResult, reasons, status)
  }
}
