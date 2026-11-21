/*
 * Copyright (C) 2025  Linked Ideal LLC.[https://linked-ideal.com/]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package analyzer

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.PropositionRelation
import com.ideal.linked.toposoid.protocol.model.base.DeductionResult
import com.ideal.linked.toposoid.protocol.model.frontend.{AnalyzedEdge, AnalyzedEdges, AnalyzedNode}
import com.ideal.linked.toposoid.protocol.model.sat.{FlattenedKnowledgeTree, FormulaSet, SatSolverResult}
import controllers.{SatInput, SentenceInfo}
import play.api.libs.json.Json
import com.ideal.linked.toposoid.common.AuthenticityType
import com.ideal.linked.toposoid.protocol.model.base.KnowledgeBaseSideInfo

class ResultAnalyzer {

  /**
   *
   * @param regulation
   * @param hypothesis
   * @return
   */
  def getAnalyzedEdges(regulation:SatInput, hypothesis:SatInput, transversalState:TransversalState): AnalyzedEdges ={
    //何故regulattionだけ
    isTrivialProposition(regulation.formulaSet, hypothesis.formulaSet) match {
      case true => {        
        val analyzedRegulationNodes:Map[String, AnalyzedNode] = regulation.parsedKnowledgeTree.sentenceInfoMap.keys.foldLeft(Map.empty[String, AnalyzedNode]){
          (acc, x) => acc ++ Map(x -> makeAnalyzedNode(x, None, regulation.trivialIdMap, regulation.parsedKnowledgeTree.sentenceInfoMap, usedSat=false))
        }

        val analyzedHypothesisNodes:Map[String, AnalyzedNode] = hypothesis.parsedKnowledgeTree.sentenceInfoMap.keys.foldLeft(Map.empty[String, AnalyzedNode]){
          (acc, x) => acc ++ Map(x -> makeAnalyzedNode(x, None, hypothesis.trivialIdMap, hypothesis.parsedKnowledgeTree.sentenceInfoMap, usedSat=false))
        }

        AnalyzedEdges(makeAnalyzedEdges(regulation.parsedKnowledgeTree.relations ::: hypothesis.parsedKnowledgeTree.relations, analyzedRegulationNodes ++ analyzedHypothesisNodes))
      }
      case _ => {
        val flattenKnowledgeTree = FlattenedKnowledgeTree(regulation.formulaSet, hypothesis.formulaSet)
        val flattenKnowledgeTreeJson:String = Json.toJson(flattenKnowledgeTree).toString()
        val satSolverResultJson:String = ToposoidUtils.callComponent(flattenKnowledgeTreeJson, conf.getString("TOPOSOID_SAT_SOLVER_WEB_HOST"), "9009", "execute", transversalState)
        val satSolverResult:SatSolverResult = Json.parse(satSolverResultJson).as[SatSolverResult]

        val sentenceInfoMapUnion:Map[String, SentenceInfo] = regulation.parsedKnowledgeTree.sentenceInfoMap ++ hypothesis.parsedKnowledgeTree.sentenceInfoMap

        val trivialIdMapUnion:Map[String, Option[DeductionResult]] = regulation.trivialIdMap ++ hypothesis.trivialIdMap
        val relationUnion:List[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])] = regulation.parsedKnowledgeTree.relations ++ hypothesis.parsedKnowledgeTree.relations
        val analyzedNodes:Map[String, AnalyzedNode] = sentenceInfoMapUnion.foldLeft(Map.empty[String, AnalyzedNode]){
          (acc, x) => acc ++ Map(x._1 -> makeAnalyzedNode(x._1, satSolverResult.satResultMap.get(x._2.satId), trivialIdMapUnion, sentenceInfoMapUnion))
        }

        AnalyzedEdges(makeAnalyzedEdges(relationUnion, analyzedNodes).distinct)
      }
    }
  }

  /**
   * This function determines if the proposition is trivial.
   * @param formulaSet
   * @return
   */
  private def isTrivialProposition(regulationFormulaSet:FormulaSet, hypothesisFormulaSet:FormulaSet) :Boolean = {
    val regulationFormulaElements = regulationFormulaSet.subFormulaMap.head._2.split(" ").toList
    val hypothesisFormulaElements = hypothesisFormulaSet.subFormulaMap.head._2.split(" ").toList
    if(regulationFormulaElements.size == 0) {
      return true
    }else if(regulationFormulaElements.size == 1) {
      // no logic relations
      if(regulationFormulaElements.filter(x => x.equals("true") || x.equals("false")).size == regulationFormulaElements.size){
        return true
      }
    }
    if(hypothesisFormulaElements.size > 0){
      //The atomic propositions of all hypotheses Does not exist in the atomic propositions of the regulation
      if(hypothesisFormulaElements.filter(x => regulationFormulaElements.filter(y => y.equals(x)).size > 0).size == 0){
        return true
      }
    }
    val nonTrivialElements =  regulationFormulaSet.subFormulaMap.foldLeft(List.empty[String]){
      (acc, x) => {
        acc ++ x._2.split(" ").filterNot(y => y.equals("AND") || y.equals("OR") || y.equals("IMP") || y.equals("true") || y.equals("false")).toList
      }
    }
    if(nonTrivialElements.size == 0) return true
    false
  }

  /**
   *
   * @param relations
   * @param analyzedNodeMap
   * @return
   */
  private def makeAnalyzedEdges(relations:List[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])], analyzedNodeMap:Map[String, AnalyzedNode]): List[AnalyzedEdge] ={
    relations.foldLeft(List.empty[AnalyzedEdge]){
      (acc, x) => {
        val premiseSentenceIds = x._1
        val premiseRelations = x._2
        val claimSentenceIds = x._3
        val claimRelations = x._4
        val otherPropositionIds = x._5
        val otherRelations = x._6

        val analyzedEdgesPremise:List[AnalyzedEdge] =  premiseRelations.size match{
          case 0 => premiseSentenceIds.size match{
              //If there is only one premise, care is taken because relation is zero
              case 1 => List(AnalyzedEdge(analyzedNodeMap.get(premiseSentenceIds.head).get, AnalyzedNode("", AuthenticityType.INSUFFICIENT.index, List.empty[KnowledgeBaseSideInfo], "", false), ""))
              case _ => List.empty[AnalyzedEdge]
            }
          case _ => premiseRelations.map(p1 => AnalyzedEdge(analyzedNodeMap.get(premiseSentenceIds(p1.sourceIndex)).get, analyzedNodeMap.get(premiseSentenceIds(p1.destinationIndex)).get, p1.operator  ) )
        }
        val analyzedEdgesClaim:List[AnalyzedEdge] =claimRelations.size match {
          case 0 => claimSentenceIds.size match{
              //If there is only one claim, care is taken because relation is zero
              case 1 => List(AnalyzedEdge(analyzedNodeMap.get(claimSentenceIds.head).get, AnalyzedNode("", AuthenticityType.INSUFFICIENT.index, List.empty[KnowledgeBaseSideInfo], "", false), ""))
              case _ => List.empty[AnalyzedEdge]
            }
          case _  => claimRelations.map(c1 => AnalyzedEdge(analyzedNodeMap.get(claimSentenceIds(c1.sourceIndex)).get, analyzedNodeMap.get(claimSentenceIds(c1.destinationIndex)).get, c1.operator  ) )
        }
        /*
        val analyzedEdgesOther:List[AnalyzedEdge] =otherRelations.size match {
          case 0 => List.empty[AnalyzedEdge]
          case _ => otherPropositionIds.filter(_.equals("-1")).size match { //If relationship has a an empty node. For example, one case with one proposition
            case 0 => otherRelations.map(o1 => AnalyzedEdge(analyzedNodeMap.get(otherPropositionIds(o1.sourceIndex)).get, analyzedNodeMap.get(otherPropositionIds(o1.destinationIndex)).get, o1.operator  ) )
            case _ => List.empty[AnalyzedEdge]
          }
        }
        */
        val analyzedEdgesOther = List.empty[AnalyzedEdge]

        //PremiseとClaimを繋げるために代表を選ぶ
        val premiseRepId = premiseSentenceIds.size match {
          case 0 => "-1"
          case _ => premiseSentenceIds.head
        }
        val claimRepId = claimSentenceIds.size match {
          case 0 => "-1"
          case _ => claimSentenceIds.head
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
   * @param propositionId
   * @param satResult
   * @param trivialPropositionIds
   * @param sentenceInfoMap
   * @param usedSat
   * @return
   */
  private def makeAnalyzedNode(sentenceId:String, satResult:Option[Boolean], triviaSentenceIds:Map[String, Option[DeductionResult]], sentenceInfoMap:Map[String, SentenceInfo], usedSat:Boolean = true) : AnalyzedNode ={

    val sentence:String = sentenceInfoMap.get(sentenceId).get.sentence
    //TODO:Implementation to assign the value of reasons    
    val status:String = usedSat match {
      case true => {
        triviaSentenceIds.keys.filter(_ == sentenceId).size match {
          case 0 => "OPTIMUM FOUND"
          case _ => "TRIVIAL"
        }
      }
      case _ => {
        triviaSentenceIds.keys.filter(_ == sentenceId).size match {
          case 0 => "UNREASONABLE"
          case _ => "TRIVIAL"
        }
      }
    }
    val finalResult:Int = status match {
      case "TRIVIAL" =>  {        
        val deductionResult = triviaSentenceIds.get(sentenceId).get.get
        deductionResult.authenticityType
      }
      case "OPTIMUM FOUND" => {
        //TODO: NO_EFFECTのケア
        satResult match {
          case Some(x) => x match {
            case true => AuthenticityType.OPTIMUM_TRUE.index
            case false => AuthenticityType.OPTIMUM_FALSE.index
          }             
          case _ => AuthenticityType.NO_EFFECT.index
        }
      } 
      case _ => {
        AuthenticityType.INSUFFICIENT.index
      }
    }
    val reasons = triviaSentenceIds.get(sentenceId).get.get.evidenceKnowledgeList
    AnalyzedNode(sentence, finalResult, reasons, status, sentenceInfoMap.get(sentenceId).get.isNegativeSentence)
  }
}
