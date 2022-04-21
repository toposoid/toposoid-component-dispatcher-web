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

import analyzer.{RequestAnalyzer, ResultAnalyzer}
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.ToposoidUtils
import com.ideal.linked.toposoid.knowledgebase.regist.model.{KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, DeductionResult}
import com.ideal.linked.toposoid.protocol.model.frontend.{AnalyzedEdges}
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentence, KnowledgeTree}
import com.ideal.linked.toposoid.protocol.model.sat.{FormulaSet}
import com.typesafe.scalalogging.LazyLogging

import javax.inject._
import play.api._
import play.api.libs.json.Json
import play.api.mvc._

case class TargetProblem(regulation:KnowledgeTree, hypothesis:KnowledgeTree)
object TargetProblem {
  implicit lazy val reader = Json.reads[TargetProblem]
}

case class ParsedKnowledgeTree( leafId:String,
                                satIdMap: Map[String, String],
                                formula:String,
                                subFormulaMap:Map[String, String],
                                analyzedSentenceObjects: List[AnalyzedSentenceObject],
                                sentenceMap:Map[String,String],
                                sentenceMapForSat:Map[String,Int],
                                relations:List[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])])

case class SatInput(parsedKnowledgeTree:ParsedKnowledgeTree,
                    trivialIdMap:Map[String, Option[DeductionResult]],
                    formulaSet :FormulaSet)

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
      val requestAnalyzer = new RequestAnalyzer()
      val json = request.body
      val inputSentence: InputSentence = Json.parse(json.toString).as[InputSentence]
      logger.info(inputSentence.premise.toString())
      logger.info(inputSentence.claim.toString())
      val knowledgeSentenceSet = KnowledgeSentenceSet(inputSentence.premise,List.empty[PropositionRelation], inputSentence.claim, List.empty[PropositionRelation])
      val parseResult:List[AnalyzedSentenceObject] = requestAnalyzer.parseKnowledgeSentence(knowledgeSentenceSet)
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
      val targetProblem:TargetProblem = Json.parse(json.toString).as[TargetProblem]
      val resultAnalyzer = new ResultAnalyzer()
      val regulationKnowledgeTree:KnowledgeTree = targetProblem.regulation
      val hypothesisKnowledgeTree:KnowledgeTree = targetProblem.hypothesis
      val satInputForRegulation:SatInput = analyzeKnowledgeTreeSub(regulationKnowledgeTree, Map.empty[String, Int])
      val satInputForHypothesis:SatInput = analyzeKnowledgeTreeSub(hypothesisKnowledgeTree, satInputForRegulation.parsedKnowledgeTree.sentenceMapForSat)
      val analyzedEdges:AnalyzedEdges = resultAnalyzer.getAnalyzedEdges(satInputForRegulation, satInputForHypothesis)
      Ok(Json.toJson(analyzedEdges)).as(JSON)
    }catch{
      case e: Exception => {
        logger.error(e.toString, e)
        BadRequest(Json.obj("status" ->"Error", "message" -> e.toString()))
      }
    }
  }

  /**
   *
   * @param knowledgeTree
   * @param sentenceMapForSat
   * @return
   */
  private def analyzeKnowledgeTreeSub(knowledgeTree:KnowledgeTree, sentenceMapForSat:Map[String, Int]): SatInput ={
    val requestAnalyzer = new RequestAnalyzer()
    val initResultObject = ParsedKnowledgeTree("-1", Map.empty[String, String], "", Map.empty[String, String], List.empty[AnalyzedSentenceObject], Map.empty[String,String], sentenceMapForSat, List.empty[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])])
    val result = requestAnalyzer.analyzeRecursive(knowledgeTree, initResultObject)
    val analyzedSentenceObjects:List[AnalyzedSentenceObject] = result.analyzedSentenceObjects
    val parseResultJson:String = Json.toJson(AnalyzedSentenceObjects(analyzedSentenceObjects)).toString()
    val deductionResult:String = ToposoidUtils.callComponent(parseResultJson, conf.getString("DEDUCTION_ADMIN_WEB_HOST"), "9003", "executeDeduction")
    val analyzedSentenceObjectsAfterDeduction = Json.parse(deductionResult).as[AnalyzedSentenceObjects]
    val (subFormulaMapAfterAssignment, trivialIdMap) = requestAnalyzer.assignTrivialProposition(analyzedSentenceObjectsAfterDeduction, result.satIdMap, result.subFormulaMap)
    val formulaSet = FormulaSet(result.formula.trim, subFormulaMapAfterAssignment)
    SatInput(result, trivialIdMap, formulaSet)
  }

}

