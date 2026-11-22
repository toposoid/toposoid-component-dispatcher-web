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

package controllers

import analyzer.{RequestAnalyzer, ResultAnalyzer}
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.InMemoryDbUtils.{getDeductionGroupEndPoints, setDeductionGroupEndPoints}
import com.ideal.linked.toposoid.common.{TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, DeductionResult}
import com.ideal.linked.toposoid.protocol.model.frontend.{AnalyzedEdges, Endpoint}
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentence, KnowledgeTree}
import com.ideal.linked.toposoid.protocol.model.sat.FormulaSet
import com.typesafe.scalalogging.LazyLogging

import javax.inject._
import play.api._
import play.api.mvc._
import play.api.libs.json.{Json, OWrites, Reads, JsValue}
import com.ideal.linked.toposoid.common.Neo4JUtilsImpl
import com.ideal.linked.toposoid.protocol.model.base.KnowledgeBaseSideInfo
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.common.FeatureType

case class TargetProblem(regulation:KnowledgeTree, hypothesis:KnowledgeTree)
object TargetProblem {
  implicit lazy val reader:Reads[TargetProblem] = Json.reads[TargetProblem]
}

case class ParsedKnowledgeTree( leafId:String,
                                formula:String,
                                subFormulaMap:Map[String, String],
                                analyzedSentenceObjectsMap: Map[String, AnalyzedSentenceObjects],
                                sentenceInfoMap:Map[String,SentenceInfo],//キーをsentencdIdにするか、SentenceInfoをリスト化するか
                                sentenceMapForSat:Map[String,Int], //タプル化, sentenceIdを含める → やらない。文章が同じであれば、同じ命題番号にしたいので。
                                relations:List[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])])

case class SentenceInfo(sentence:String, isNegativeSentence:Boolean, satId:String)


case class SatInput(parsedKnowledgeTree:ParsedKnowledgeTree,
                    trivialIdMap:Map[String, Option[DeductionResult]],
                    formulaSet :FormulaSet)

case class ReqSelector(index:Int, function:Endpoint)
object ReqSelector {
  implicit val jsonWrites:OWrites[ReqSelector] = Json.writes[ReqSelector]
  implicit val jsonReads:Reads[ReqSelector] = Json.reads[ReqSelector]
}


case class Evidence(documentId:String, sentences:List[String], images:List[String]=List.empty[String], tables:List[String]=List.empty[String])
object Evidence {
  implicit val jsonWrites:OWrites[Evidence] = Json.writes[Evidence]
  implicit val jsonReads:Reads[Evidence] = Json.reads[Evidence]
}


/**
 * This controller creates an `Action` to integrates two major microservices.
 * One is a microservice that analyzes the predicate argument structure of sentences,
 * and the other is a microservice that makes logical inferences.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) extends BaseController  with LazyLogging{


  /**
   * This function parses a tree-structured and logical expression including sentence.
   * Input / output is request, response and REST in json.
   * This output provides information for inference in the SAT.
   * @return
   */
  def analyzeKnowledgeTree():Action[JsValue] = Action(parse.json[JsValue])  { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE .str).get).as[TransversalState]
    try {
      val json = request.body
      logger.info(ToposoidUtils.formatMessageForLogger(json.toString(), transversalState.userId))
      val targetProblem:TargetProblem = Json.parse(json.toString).as[TargetProblem]
      val resultAnalyzer = new ResultAnalyzer()
      val regulationKnowledgeTree:KnowledgeTree = targetProblem.regulation
      val hypothesisKnowledgeTree:KnowledgeTree = targetProblem.hypothesis
      val satInputForRegulation:SatInput = analyzeKnowledgeTreeSub(regulationKnowledgeTree, Map.empty[String, Int], transversalState)
      val satInputForHypothesis:SatInput = analyzeKnowledgeTreeSub(hypothesisKnowledgeTree, satInputForRegulation.parsedKnowledgeTree.sentenceMapForSat, transversalState)
      val analyzedEdges:AnalyzedEdges = resultAnalyzer.getAnalyzedEdges(satInputForRegulation, satInputForHypothesis, transversalState)
      val analyzedResponse =Json.toJson(analyzedEdges)
      logger.info(ToposoidUtils.formatMessageForLogger(analyzedResponse.toString(), transversalState.userId))
      logger.info(ToposoidUtils.formatMessageForLogger("dispatching deduction component completed.", transversalState.userId))
      Ok(analyzedResponse).as(JSON)
    }catch{
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.userId), e)
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
  private def analyzeKnowledgeTreeSub(knowledgeTree:KnowledgeTree, sentenceMapForSat:Map[String, Int], transversalState:TransversalState): SatInput ={
    val requestAnalyzer = new RequestAnalyzer()
    val initResultObject = ParsedKnowledgeTree("-1", "", Map.empty[String, String], Map.empty[String, AnalyzedSentenceObjects], Map.empty[String,SentenceInfo], sentenceMapForSat, List.empty[(List[String], List[PropositionRelation], List[String], List[PropositionRelation], List[String], List[PropositionRelation])])
    val result = requestAnalyzer.analyzeRecursive(knowledgeTree, initResultObject, transversalState)

    val analyzedSentenceObjectsAfterDeduction:List[AnalyzedSentenceObject] = conf.getString("TOPOSOID_DEDUCTION_ADMIN_SKIP") match {
      case "1" => {
        result.analyzedSentenceObjectsMap.values.foldLeft(List.empty[AnalyzedSentenceObject]) {
          (acc, asos) => {
            acc ++ asos.analyzedSentenceObjects
          }
        }
      }
      case _ => {
        result.analyzedSentenceObjectsMap.values.foldLeft(List.empty[AnalyzedSentenceObject]) {
          (acc, asos) => {
            //The processing unit of DEDUCTION_ADMIN_WEB is KnowledgeSentenceSet.
            val deductionResult: String = ToposoidUtils.callComponent(Json.toJson(asos).toString(), conf.getString("TOPOSOID_DEDUCTION_ADMIN_WEB_HOST"), conf.getString("TOPOSOID_DEDUCTION_ADMIN_WEB_PORT"), "executeDeduction", transversalState)
            acc ++ Json.parse(deductionResult).as[AnalyzedSentenceObjects].analyzedSentenceObjects
          }
        }
      }
    }
    val (subFormulaMapAfterAssignment, trivialIdMap) = requestAnalyzer.assignTrivialProposition(analyzedSentenceObjectsAfterDeduction, result.sentenceInfoMap, result.subFormulaMap)
    val formulaSet = FormulaSet(result.formula.trim, subFormulaMapAfterAssignment)    
    SatInput(result, trivialIdMap, formulaSet)
  }

  /**
   * This function receives the URL information of the microservice as JSON and
   * Register and update microservices that perform deductive reasoning
   *
   * @return
   */
  def changeEndPoints():Action[JsValue] = Action(parse.json[JsValue]) { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE.str).get).as[TransversalState]
    try {
      val json = request.body
      val endPoints: Seq[Endpoint] = Json.parse(json.toString).as[Seq[Endpoint]]
      val updatedEndPoints: Seq[Endpoint] = setDeductionGroupEndPoints(endPoints, transversalState)
      logger.info(ToposoidUtils.formatMessageForLogger("Changing End-Points completed." + updatedEndPoints.toString(), transversalState.userId))
      Ok("""{"status":"OK"}""").as(JSON)
    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.userId), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  def getEndPointsFromInMemoryDB():Action[JsValue] = Action(parse.json[JsValue])  { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE.str).get).as[TransversalState]
    try {
      Ok(Json.toJson(getDeductionGroupEndPoints(transversalState))).as(JSON)
    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.userId), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }

  def getEvidenceSentenceAndNeighborhood():Action[JsValue] = Action(parse.json[JsValue])  { request =>
    val transversalState = Json.parse(request.headers.get(TRANSVERSAL_STATE.str).get).as[TransversalState]
    try {
      val json = request.body
      val knowledgeBaseSideInfoList: List[KnowledgeBaseSideInfo] = Json.parse(json.toString).as[List[KnowledgeBaseSideInfo]]
      val neo4JUtils = Neo4JUtilsImpl()      
      val evidences =knowledgeBaseSideInfoList.foldLeft(List.empty[Evidence]){
        (acc, x) => {
          val jsonStr = neo4JUtils.getCypherQueryResult("MATCH (n) WHERE n.propositionId='%s' RETURN n".format(x.propositionId) , "", transversalState)     
          val neo4jRecords: Neo4jRecords = Json.parse(jsonStr).as[Neo4jRecords]   
          val evidenceInfoList = neo4jRecords.records.foldLeft(List.empty[Evidence]) {
            (acc2, y) => {   
              val evidenceInfo = y.map(z => {       
                val featureInfo:Option[(Int, String)] = z.value.featureNode match {
                  case Some(a) => {
                    Option((a.featureType, a.url))
                  }
                  case _ => None 
                }                
                val(documentId, sentenceId, sentence) = z.value.semiGlobalNode match {
                  case Some(a) => {
                    (a.documentId, a.sentenceId, a.sentence)
                  }
                  case _ => ("", "", "")
                }
                val imageUrl = featureInfo match {
                  case Some(x) => {
                    x._1 match {
                      case FeatureType.IMAGE.index => x._2
                      case _ => ""
                    }
                  }
                  case _ => ""
                }
                val tableUrl = featureInfo match {
                  case Some(x) => {
                    x._1 match {
                      case FeatureType.TABLE.index => x._2
                      case _ => ""
                    }
                  }
                  case _ => ""
                }
                (documentId, sentenceId, sentence, imageUrl, tableUrl)
              })

              //propositionIdごとにデータは処理されるので、propositionIdが同じ範囲ではuuidで序列が文章の順番になる。
              val documentInfo = evidenceInfo.filterNot(_._1.equals(""))
              val documentId = documentInfo.size match {
                case 0 => ""
                case _ => documentInfo.head._1
              }
                            
              val sentences = evidenceInfo.filterNot(_._2.equals("")).map(a => (a._2 -> a._3)).sortBy(_._1).toSeq.map(_._2)
              val imageUrls = evidenceInfo.filterNot(_._4.equals("")).map(_._4)
              val tableUrls = evidenceInfo.filterNot(_._5.equals("")).map(_._5)              
              acc2 :+ Evidence(documentId, sentences, imageUrls, tableUrls)

            }
          } 

          val documentInfo = evidenceInfoList.filterNot(_.documentId.equals(""))
          val documentId = documentInfo.size match {
            case 0 => ""
            case _ => documentInfo.head._1
          }
          val aggregatedSentences = evidenceInfoList.filterNot(_.sentences.size == 0).foldLeft(List.empty[String]){(acc, a) => acc ++ a.sentences}.distinct
          val aggregatedImageUrls = evidenceInfoList.filterNot(_.images.size == 0).foldLeft(List.empty[String]){(acc, a) => acc ++ a.images}.distinct
          val aggregatedTableUrls = evidenceInfoList.filterNot(_.tables.size == 0).foldLeft(List.empty[String]){(acc, a) => acc ++ a.tables}.distinct

          acc :+  Evidence(documentId, aggregatedSentences, aggregatedImageUrls, aggregatedTableUrls)        
        }
      }            

      Ok(Json.toJson(evidences.filterNot(x => x.sentences.size == 0) .distinct)).as(JSON)
    } catch {
      case e: Exception => {
        logger.error(ToposoidUtils.formatMessageForLogger(e.toString, transversalState.userId), e)
        BadRequest(Json.obj("status" -> "Error", "message" -> e.toString()))
      }
    }
  }
}

