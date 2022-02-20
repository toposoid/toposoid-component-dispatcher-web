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
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentence, KnowledgeLeaf, KnowledgeNode, KnowledgeTree}
import com.ideal.linked.toposoid.protocol.model.sat.FlattenedKnowledgeTree
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
                                sentenceMap:Map[String,String])
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
      val initResultObject = ParsedKnowledgeTree(-1, Map.empty[String, String], "", Map.empty[String, String], List.empty[AnalyzedSentenceObject], Map.empty[String,String])
      val result = this.analyzeRecursive(knowledgeTree, initResultObject)
      val analyzedSentenceObjects:List[AnalyzedSentenceObject] = result.analyzedSentenceObjects
      val parseResultJson:String = Json.toJson(AnalyzedSentenceObjects(analyzedSentenceObjects)).toString()
      val deductionResult:String = ToposoidUtils.callComponent(parseResultJson, conf.getString("DEDUCTION_ADMIN_WEB_HOST"), "9003", "executeDeduction")
      val analyzedSentenceObjectsAfterDeduction = Json.parse(deductionResult).as[AnalyzedSentenceObjects]
      val subFormulaMapAfterAssignment = assignTrivialProposition(analyzedSentenceObjectsAfterDeduction, result.satIdMap, result.subFormulaMap)

      val reverseSatIdMap =  (result.satIdMap.values zip result.satIdMap.keys).groupBy(_._1).mapValues(_.map(_._2).head)
      //result.sentenceMap.map(x => println(reverseSatIdMap.get(x._1).get, x._2))

      val flattenKnowledgeTree = FlattenedKnowledgeTree(result.satIdMap, result.formula.trim, subFormulaMapAfterAssignment)
      Ok(Json.toJson(flattenKnowledgeTree)).as(JSON)
    }catch{
      case e: Exception => {
        logger.error(e.toString, e)
        BadRequest(Json.obj("status" ->"Error", "message" -> e.toString()))
      }
    }
  }


  /**
   * This function performs the assignment of propositions whose truth is obvious from the knowledge graph
   * @param analyzedSentenceObjects
   * @param satIdMap
   * @param subFormulaMap
   * @return
   */
  private def assignTrivialProposition(analyzedSentenceObjects:AnalyzedSentenceObjects, satIdMap:Map[String, String], subFormulaMap:Map[String, String]): Map[String, String] ={

    //print(analyzedSentenceObjects.analyzedSentenceObjects.map(_.nodeMap.map(x => x._2.propositionId)).distinct.size)

    val reverseSatIdMap =  (satIdMap.values zip satIdMap.keys).groupBy(_._1).mapValues(_.map(_._2).head)
    //This is a list of PropositionIds that can be found to be true or false as a result of searching GraphDB.
    val trivialPropositionIds:Map[String,Option[Boolean]] =
      analyzedSentenceObjects.analyzedSentenceObjects.foldLeft(Map.empty[String, Option[Boolean]]){
        (acc, x) =>
          acc ++ extractDeductionResult(x)
      }
    //This is converting the positionId to satId
    val trivialSatIds = trivialPropositionIds.foldLeft(Map.empty[String, Option[Boolean]]){
      (acc, x) => x._2 match {
        case Some(_) => acc ++ Map(reverseSatIdMap.get(x._1).get -> x._2)
        case _ => acc
      }
    }

    //This is the assignment of trivial propositional truth values to formula
    subFormulaMap.foldLeft(Map.empty[String, String]){
      (acc, x) =>
        acc ++ Map(x._1 -> assignment(x._2, trivialSatIds))
    }
  }

  /**
   * True/False assignment
   * @param subFormula
   * @param trivialSatIds
   * @return
   */
  private def assignment(subFormula:String, trivialSatIds:Map[String, Option[Boolean]]): String ={
    subFormula.split(" ").map(x => trivialSatIds.isDefinedAt(x) match  {
      case true => trivialSatIds.get(x).get.get
      case false => x
    }).mkString(" ")
  }

  /**
   * This function extracts a trivial proposition as a result of searching GraphDB.
   * @param analyzedSentenceObject
   * @return
   */
  private def extractDeductionResult(analyzedSentenceObject:AnalyzedSentenceObject): Map[String, Option[Boolean]] ={
    //An analyzedSentenceObject always has one propositionId.
    val propositionId = analyzedSentenceObject.nodeMap.map(_._2.propositionId).head

    //An analyzedSentenceObject has GraphDB search results of either Premis or Claim depending on the sentenceType.
    val deductionResult:DeductionResult = analyzedSentenceObject.deductionResultMap.get(analyzedSentenceObject.sentenceType.toString).get
    val status:Option[Boolean] = deductionResult.matchedPropositionIds.size match {
      case 0 => None
      case _ => Some(deductionResult.status)
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
        val r2 = analyzeRecursive(right, r1)
        val newFormula = r1.leafId.toInt match {
          case -1 => "%s %s %s".format(r2.formula, r2.leafId, v)
          case _ => "%s %s %s %s".format(r2.formula, r1.leafId, r2.leafId, v)
        }
        ParsedKnowledgeTree(-1,  r2.satIdMap, newFormula, r2.subFormulaMap, r2.analyzedSentenceObjects, r2.sentenceMap)
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

        val premiseIds:List[String] = (leafId to leafId + premisePropositionIds.size -1).map(_.toString).toList
        val currentMaxId2:Int = leafId + premiseIds.size
        val claimIds:List[String] = (currentMaxId2 to currentMaxId2 + claimPropositionIds.size -1).map(_.toString).toList

        val newPremiseSATidMap:Map[String, String] =  (premiseIds zip premisePropositionIds).groupBy(_._1).mapValues(_.map(_._2).head)
        val newClaimSATidMap:Map[String, String] =  (claimIds zip claimPropositionIds).groupBy(_._1).mapValues(_.map(_._2).head)
        val newSatIdMap = newPremiseSATidMap ++ newClaimSATidMap ++ result.satIdMap

        val premiseFormula:String = makeFormula(premiseIds, v.premiseLogicRelation)
        val claimFormula:String = makeFormula(claimIds, v.claimLogicRelation)
        val subFormula = "%s %s IMP".format(premiseFormula, claimFormula)
        val subFormulaMap = result.subFormulaMap ++ Map(leafId.toString -> subFormula)
        ParsedKnowledgeTree(leafId,  newSatIdMap, result.formula, subFormulaMap, result.analyzedSentenceObjects:::parseResult, result.sentenceMap ++ sentenceMap)
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

    if(atoms.size == 1 && relations.size == 0) return atoms.head
    val formulas:List[String] = relations.map(x => atoms(x.sourceIndex) + " " + atoms(x.destinationIndex) + " " + x.operator)
    if(formulas.size == 1) return formulas.head
    formulas.drop(1).foldLeft(formulas.head){
      (acc, x) => acc + " " + x + " AND"
    }.trim

  }

}

