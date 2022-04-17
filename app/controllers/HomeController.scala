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

package controllers

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{CLAIM, PREMISE, ToposoidUtils}
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeBaseNode
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, DeductionResult}
import com.ideal.linked.toposoid.protocol.model.frontend.{AnalyzedEdge, AnalyzedEdges, AnalyzedNode}
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentence, KnowledgeLeaf, KnowledgeNode, KnowledgeTree}
import com.ideal.linked.toposoid.protocol.model.sat.{FlattenedKnowledgeTree, SatSolverResult}
import com.typesafe.scalalogging.LazyLogging

import javax.inject._
import play.api._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}

case class ParsedKnowledgeTree( leafId:Int,
                                satIdMap: Map[String, String],
                                formula:String,
                                subFormulaMap:Map[String, String],
                                analyzedSentenceObjects: List[AnalyzedSentenceObject],
                                sentenceMap:Map[String,String],
                                relations:List[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])])

/**
 * This controller creates an `Action` to integrates two major microservices.
 * One is a microservice that analyzes the predicate argument structure of sentences,
 * and the other is a microservice that makes logical inferences.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController  with LazyLogging{

  /**
   * This function receives sentences as JSON. Sentences can be set in JSON separately for assumptions and claims.
   * Matches with the knowledge database and returns the result of the logical solution in JSON.
   * @return
   */
  def analyze() = Action(parse.json) { request =>
    try {
      val json = request.body
      val inputSentence: InputSentence = Json.parse(json.toString).as[InputSentence]
      logger.info(inputSentence.premise.toString())
      logger.info(inputSentence.claim.toString())
      val knowledgeSentenceSet = KnowledgeSentenceSet(inputSentence.premise,List.empty[PropositionRelation], inputSentence.claim, List.empty[PropositionRelation])
      val parseResult:List[AnalyzedSentenceObject] = this.parseKnowledgeSentence(knowledgeSentenceSet)
      val parseResultJson:String = Json.toJson(AnalyzedSentenceObjects(parseResult)).toString()
      val deductionResult:String = ToposoidUtils.callComponent(parseResultJson, conf.getString("DEDUCTION_ADMIN_WEB_HOST"), "9003", "executeDeduction")
      Ok(deductionResult).as(JSON)
    }catch{
      case e: Exception => {
        logger.error(e.toString, e)
        BadRequest(Json.obj("status" ->"Error", "message" -> e.toString()))
      }
    }
  }

  /**
   * This function parses a tree-structured and logical expression including sentence.
   * Input / output is request, response and REST in json.
   * This output provides information for inference in the SAT.
   * @return
   */
  def analyzeKnowledgeTree() = Action(parse.json) { request =>
    try {
      val json = request.body
      val knowledgeTree: KnowledgeTree = Json.parse(json.toString).as[KnowledgeTree]
      val initResultObject = ParsedKnowledgeTree(-1, Map.empty[String, String], "", Map.empty[String, String], List.empty[AnalyzedSentenceObject], Map.empty[String,String], List.empty[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])])
      val result = this.analyzeRecursive(knowledgeTree, initResultObject)
      val analyzedSentenceObjects:List[AnalyzedSentenceObject] = result.analyzedSentenceObjects
      val parseResultJson:String = Json.toJson(AnalyzedSentenceObjects(analyzedSentenceObjects)).toString()
      val deductionResult:String = ToposoidUtils.callComponent(parseResultJson, conf.getString("DEDUCTION_ADMIN_WEB_HOST"), "9003", "executeDeduction")
      val analyzedSentenceObjectsAfterDeduction = Json.parse(deductionResult).as[AnalyzedSentenceObjects]
      val (subFormulaMapAfterAssignment, trivialIdMap) = assignTrivialProposition(analyzedSentenceObjectsAfterDeduction, result.satIdMap, result.subFormulaMap)
      //val reverseSatIdMap =  (result.satIdMap.values zip result.satIdMap.keys).groupBy(_._1).mapValues(_.map(_._2).head)
      //result.sentenceMap.map(x => println(reverseSatIdMap.get(x._1).get, x._2))

      val flattenKnowledgeTree = FlattenedKnowledgeTree(result.formula.trim, subFormulaMapAfterAssignment)
      val analyzedEdges:AnalyzedEdges = this.isTrivialProposition(flattenKnowledgeTree) match {
        case true => {
          val analyzedNodes:Map[String, AnalyzedNode] = result.satIdMap.keys.foldLeft(Map.empty[String, AnalyzedNode]){
            (acc, x) => acc ++ Map(x -> makeAnalyzedNode(x, false, trivialIdMap, result.sentenceMap, result.satIdMap, usedSat=false))
          }
          AnalyzedEdges(makeAnalyzedEdges(result.relations, analyzedNodes))
        }
        case _ => {
          val flattenKnowledgeTreeJson:String = Json.toJson(flattenKnowledgeTree).toString()
          val satSolverResultJson:String = ToposoidUtils.callComponent(flattenKnowledgeTreeJson, conf.getString("TOPOSOID_SAT_SOLVER_WEB_HOST"), "9009", "execute")
          val satSolverResult:SatSolverResult = Json.parse(satSolverResultJson).as[SatSolverResult]

          val effectiveSstSolverResult:Map[String, Boolean] = satSolverResult.satResultMap.filter(x => x._1.toInt <= result.satIdMap.keys.map(_.toInt).max.toInt)
          //satSolverResultが空だったら、Unsatisfied、空でなかったら、Optinum Found
          val analyzedNodes:Map[String, AnalyzedNode] = effectiveSstSolverResult.foldLeft(Map.empty[String, AnalyzedNode]){
            (acc, x) => acc ++  Map(x._1 -> makeAnalyzedNode(x._1, x._2, trivialIdMap, result.sentenceMap, result.satIdMap))
          }
          AnalyzedEdges(makeAnalyzedEdges(result.relations, analyzedNodes))
        }
      }
      /*
      val flattenKnowledgeTreeJson:String = Json.toJson(flattenKnowledgeTree).toString()
      val satSolverResultJson:String = ToposoidUtils.callComponent(flattenKnowledgeTreeJson, conf.getString("TOPOSOID_SAT_SOLVER_WEB_HOST"), "9009", "execute")
      val satSolverResult:SatSolverResult = Json.parse(satSolverResultJson).as[SatSolverResult]

      val effectiveSstSolverResult:Map[String, Boolean] = satSolverResult.satResultMap.filter(x => x._1.toInt <= result.satIdMap.keys.map(_.toInt).max.toInt)
      //satSolverResultが空だったら、Unsatisfied、空でなかったら、Optinum Found
      val analyzedNodes:Map[String, AnalyzedNode] = effectiveSstSolverResult.foldLeft(Map.empty[String, AnalyzedNode]){
        (acc, x) => acc ++  Map(x._1 -> makeAnalyzedNode(x._1, x._2, trivialIdMap, result.sentenceMap, result.satIdMap))
      }
      val analyzedEdges:AnalyzedEdges = AnalyzedEdges(makeAnalyzedEdges(result.relations, analyzedNodes))
       */
      Ok(Json.toJson(analyzedEdges)).as(JSON)
    }catch{
      case e: Exception => {
        logger.error(e.toString, e)
        BadRequest(Json.obj("status" ->"Error", "message" -> e.toString()))
      }
    }
  }

  /**
   * This function determines if the proposition is trivial.
   * @param flattenKnowledgeTree
   * @return
   */
  def isTrivialProposition(flattenKnowledgeTree:FlattenedKnowledgeTree) :Boolean = {
    val formulaElements = flattenKnowledgeTree.subFormulaMap.head._2.split(" ")
    if(formulaElements.size <= 1) return true
    val nonTrivialElements =  flattenKnowledgeTree.subFormulaMap.foldLeft(List.empty[String]){
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
          case 0 => {
            premiseSatIds.size match{
              //If there is only one premise, care is taken because relation is zero
              case 1 => List(AnalyzedEdge(analyzedNodeMap.get(premiseSatIds.head).get, AnalyzedNode("", false, List.empty[String], ""), ""))
              case _ => List.empty[AnalyzedEdge]
            }
          }
          case _ => premiseRelations.map(p1 => AnalyzedEdge(analyzedNodeMap.get(premiseSatIds(p1.sourceIndex)).get, analyzedNodeMap.get(premiseSatIds(p1.destinationIndex)).get, p1.operator  ) )
        }
        val analyzedEdgesClaim:List[AnalyzedEdge] =claimRelations.size match {
          case 0 => {
            claimSatIds.size match{
              //If there is only one claim, care is taken because relation is zero
              case 1 => List(AnalyzedEdge(analyzedNodeMap.get(claimSatIds.head).get, AnalyzedNode("", false, List.empty[String], ""), ""))
              case _ => List.empty[AnalyzedEdge]
            }
          }
          case _  =>claimRelations.map(c1 => AnalyzedEdge(analyzedNodeMap.get(claimSatIds(c1.sourceIndex)).get, analyzedNodeMap.get(claimSatIds(c1.destinationIndex)).get, c1.operator  ) )
        }
        val analyzedEdgesOther:List[AnalyzedEdge] =otherRelations.size match {
          case 0 => List.empty[AnalyzedEdge]
          case _  =>otherRelations.map(o1 => AnalyzedEdge(analyzedNodeMap.get(otherSatIds(o1.sourceIndex)).get, analyzedNodeMap.get(otherSatIds(o1.destinationIndex)).get, o1.operator  ) )
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
  private def makeAnalyzedNode(satId:String, satResult:Boolean, trivialPropositionIds:Map[String, Option[DeductionResult]], sentenceMap:Map[String, String], satIdMap:Map[String,String], usedSat:Boolean = true) : AnalyzedNode ={

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

  /**
   * This function performs the assignment of propositions whose truth is obvious from the knowledge graph
   * @param analyzedSentenceObjects
   * @param satIdMap
   * @param subFormulaMap
   * @return
   */
  private def assignTrivialProposition(analyzedSentenceObjects:AnalyzedSentenceObjects, satIdMap:Map[String, String], subFormulaMap:Map[String, String]): (Map[String, String], Map[String, Option[DeductionResult]])  ={

    //print(analyzedSentenceObjects.analyzedSentenceObjects.map(_.nodeMap.map(x => x._2.propositionId)).distinct.size)

    val reverseSatIdMap =  (satIdMap.values zip satIdMap.keys).groupBy(_._1).mapValues(_.map(_._2).head)
    //This is a list of PropositionIds that can be found to be true or false as a result of searching GraphDB.
    val trivialPropositionIds:Map[String,Option[DeductionResult]] =
      analyzedSentenceObjects.analyzedSentenceObjects.foldLeft(Map.empty[String, Option[DeductionResult]]){
        (acc, x) =>
          acc ++ extractDeductionResult(x)
      }
    //This is converting the positionId to satId
    val trivialSatIds = trivialPropositionIds.foldLeft(Map.empty[String, Boolean]){
      (acc, x) => x._2 match {
        case Some(_) => acc ++ Map(reverseSatIdMap.get(x._1).get -> x._2.get.status)
        case _ => acc
      }
    }

    //This is the assignment of trivial propositional truth values to formula
    val resultMap:Map[String, String] = subFormulaMap.foldLeft(Map.empty[String, String]){
      (acc, x) =>
        acc ++ Map(x._1 -> assignment(x._2, trivialSatIds))
    }
    (resultMap, trivialPropositionIds.filterNot(_._2 == None))
  }

  /**
   * True/False assignment
   * @param subFormula
   * @param trivialSatIds
   * @return
   */
  private def assignment(subFormula:String, trivialSatIds:Map[String, Boolean]): String ={
    subFormula.split(" ").map(x => trivialSatIds.isDefinedAt(x) match  {
      case true => trivialSatIds.get(x).get
      case false => x
    }).mkString(" ")
  }

  /**
   * This function extracts a trivial proposition as a result of searching GraphDB.
   * @param analyzedSentenceObject
   * @return
   */
  private def extractDeductionResult(analyzedSentenceObject:AnalyzedSentenceObject): Map[String, Option[DeductionResult]] ={
    //An analyzedSentenceObject always has one propositionId.
    val propositionId = analyzedSentenceObject.nodeMap.map(_._2.propositionId).head

    //An analyzedSentenceObject has GraphDB search results of either Premis or Claim depending on the sentenceType.
    val deductionResult:DeductionResult = analyzedSentenceObject.deductionResultMap.get(analyzedSentenceObject.sentenceType.toString).get
    val status:Option[DeductionResult] = deductionResult.matchedPropositionIds.size match {
      case 0 => None
      case _ => Some(deductionResult)
    }
    Map(propositionId -> status)
  }

  /**
   * This function delegates the processing of a given sentence to passer for each multilingual.
   * @param knowledgeSentenceSet
   * @return
   */
  private def parseKnowledgeSentence(knowledgeSentenceSet: KnowledgeSentenceSet):List[AnalyzedSentenceObject] = Try{

    val premiseJapanese:List[Knowledge] = knowledgeSentenceSet.premiseList.filter(_.lang == "ja_JP")
    val claimJapanese:List[Knowledge] = knowledgeSentenceSet.claimList.filter(_.lang == "ja_JP")
    val premiseEnglish:List[Knowledge] = knowledgeSentenceSet.premiseList.filter(_.lang.startsWith("en_"))
    val claimEnglish:List[Knowledge] = knowledgeSentenceSet.claimList.filter(_.lang.startsWith("en_"))
    val japaneseInputSentences:String = Json.toJson(InputSentence(premiseJapanese, claimJapanese)).toString()
    val englishInputSentences:String = Json.toJson(InputSentence(premiseEnglish, claimEnglish)).toString()

    val numOfKnowledgeJapanese = premiseJapanese.size + claimJapanese.size
    val numOfKnowledgeEnglish = premiseEnglish.size + claimEnglish.size

    val deductionJapaneseList:List[AnalyzedSentenceObject] = numOfKnowledgeJapanese match{
      case 0 =>
        List.empty[AnalyzedSentenceObject]
      case _ =>
        val parseResultJapanese:String = ToposoidUtils.callComponent(japaneseInputSentences ,conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
        Json.parse(parseResultJapanese).as[AnalyzedSentenceObjects].analyzedSentenceObjects
    }

    val deductionEnglishList:List[AnalyzedSentenceObject] = numOfKnowledgeEnglish match{
      case 0 =>
        List.empty[AnalyzedSentenceObject]
      case _ =>
        val parseResultEnglish:String = ToposoidUtils.callComponent(englishInputSentences ,conf.getString("SENTENCE_PARSER_EN_WEB_HOST"), "9007", "analyze")
        Json.parse(parseResultEnglish).as[AnalyzedSentenceObjects].analyzedSentenceObjects
    }

    return deductionJapaneseList ::: deductionEnglishList

  }match {
    case Success(s) => s
    case Failure(e) => throw e
  }

  /**
   * This function recursively analyzes logical expressions in a tree structure and sentences.
   * @param t
   * @param result
   * @return
   */
  private def analyzeRecursive(t : KnowledgeTree, result:ParsedKnowledgeTree):ParsedKnowledgeTree = {
    t match {
      case KnowledgeNode(v, left, right) =>
        val r1 = analyzeRecursive(left, result)
        if(v != "AND" && v != "OR") return ParsedKnowledgeTree(-1,  r1.satIdMap, "1", r1.subFormulaMap, r1.analyzedSentenceObjects, r1.sentenceMap, r1.relations)
        val r2 = analyzeRecursive(right, r1)
        val newFormula = r1.leafId.toInt match {
          case -1 => "%s %s %s".format(r2.formula, r2.leafId, v)
          case _ => "%s %s %s %s".format(r2.formula, r1.leafId, r2.leafId, v)
        }
        val relations = r1.leafId match {
          case -1 => List((List.empty[String], List.empty[PropositionRelation], List.empty[String], List.empty[PropositionRelation], List("1", r2.leafId.toString), List(PropositionRelation(v, 0, 1))))
          case _ => List((List.empty[String], List.empty[PropositionRelation], List.empty[String], List.empty[PropositionRelation], List(r1.leafId.toString, r2.leafId.toString), List(PropositionRelation(v, 0, 1)) ))
        }
        ParsedKnowledgeTree(-1,  r2.satIdMap, newFormula, r2.subFormulaMap, r2.analyzedSentenceObjects, r2.sentenceMap, r2.relations ++ relations)
      case KnowledgeLeaf(v) =>
        //print("%s ".format(v.premiseList.map(_.sentence).mkString(",")))
        //parse v
        val parseResult = this.parseKnowledgeSentence(v)

        val sentenceMap = parseResult.foldLeft(Map.empty[String, String]){
          (acc, x) => acc ++ Map(x.nodeMap.head._2.propositionId -> getSentence(x.nodeMap))
        }

        //Extract all the positionIds contained in the leaf while keeping the order.
        val premisePropositionIds:List[String] = parseResult.filter(_.sentenceType == PREMISE.index).map(_.nodeMap.head._2.propositionId).distinct
        val claimPropositionIds:List[String] = parseResult.filter(_.sentenceType == CLAIM.index).map(_.nodeMap.head._2.propositionId).distinct

        val leafId:Int = result.satIdMap.keys.size match {
          case 0 => 1
          case _ => result.satIdMap.keys.map(_.toInt).max + 1
        }

        //This process assigns an ID smaller than the premise to claim. The reason is that some propositions do not have a premise.
        val claimIds:List[String] = (leafId to leafId + claimPropositionIds.size -1).map(_.toString).toList
        val currentMaxId2:Int = leafId + claimIds.size
        val premiseIds:List[String] = (currentMaxId2 to currentMaxId2 + premisePropositionIds.size -1).map(_.toString).toList

        val newPremiseSATidMap:Map[String, String] =  (premiseIds zip premisePropositionIds).groupBy(_._1).mapValues(_.map(_._2).head)
        val newClaimSATidMap:Map[String, String] =  (claimIds zip claimPropositionIds).groupBy(_._1).mapValues(_.map(_._2).head)
        val newSatIdMap = newPremiseSATidMap ++ newClaimSATidMap ++ result.satIdMap

        val premiseFormula:String = makeFormula(premiseIds, v.premiseLogicRelation)
        val claimFormula:String = makeFormula(claimIds, v.claimLogicRelation)
        val subFormula = premiseFormula match {
          case "" => claimFormula
          case _ => "%s %s IMP".format(premiseFormula, claimFormula)
        }
        val subFormulaMap = result.subFormulaMap ++ Map(leafId.toString -> subFormula)
        val relations = result.relations ++ List((premiseIds, v.premiseLogicRelation, claimIds, v.claimLogicRelation, List.empty[String], List.empty[PropositionRelation]))

        ParsedKnowledgeTree(leafId,  newSatIdMap, result.formula, subFormulaMap, result.analyzedSentenceObjects:::parseResult, result.sentenceMap ++ sentenceMap, relations)
    }
  }


  private def getSentence(nodeMap:Map[String,KnowledgeBaseNode]): String ={
    nodeMap.head._2.lang match {
      case "ja_JP" => ListMap(nodeMap.toSeq.sortBy(_._2.currentId):_*).map(_._2.surface).mkString
      case _ => ListMap(nodeMap.toSeq.sortBy(_._2.currentId):_*).map(_._2.surface).mkString(" ")
    }
  }

  /**
   *　Configure logical expressions contained in KnowledgeSentenceSet
   * @param atoms
   * @param relations
   * @return
   */
  private def makeFormula(atoms:List[String], relations:List[PropositionRelation]): String ={

    if(atoms.size == 0 && relations.size == 0) return ""
    if(atoms.size == 1 && relations.size == 0) return atoms.head
    val formulas:List[String] = relations.map(x => atoms(x.sourceIndex) + " " + atoms(x.destinationIndex) + " " + x.operator)
    if(formulas.size == 1) return formulas.head
    formulas.drop(1).foldLeft(formulas.head){
      (acc, x) => acc + " " + x + " AND"
    }.trim

  }

}

