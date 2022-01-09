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
import com.ideal.linked.toposoid.common.ToposoidUtils
import com.ideal.linked.toposoid.knowledgebase.regist.model.Knowledge
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects}
import com.ideal.linked.toposoid.protocol.model.parser.InputSentence
import com.typesafe.scalalogging.LazyLogging

import javax.inject._
import play.api._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._

import scala.util.{Failure, Success}

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

      val premiseJapanese:List[Knowledge] = inputSentence.premise.filter(_.lang == "ja_JP")
      val claimJapanese:List[Knowledge] = inputSentence.claim.filter(_.lang == "ja_JP")
      val premiseEnglish:List[Knowledge] = inputSentence.premise.filter(_.lang.startsWith("en_"))
      val claimEnglish:List[Knowledge] = inputSentence.claim.filter(_.lang.startsWith("en_"))
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

      val parseResult:List[AnalyzedSentenceObject] = deductionJapaneseList ::: deductionEnglishList
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

}

