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

import akka.util.Timeout
import com.ideal.linked.data.accessor.neo4j.Neo4JAccessor
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.sat.FlattenedKnowledgeTree
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentAsString, contentType, defaultAwaitTimeout, status, _}
import play.api.test.{FakeRequest, _}

import scala.concurrent.duration.DurationInt

class HomeControllerSpecJapanese extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite  with DefaultAwaitTimeout with Injecting{

  override def beforeAll(): Unit = {
    Neo4JAccessor.delete()
    Sentence2Neo4jTransformer.createGraphAuto(List(Knowledge("案ずるより産むが易し。", "ja_JP", "{}")))
    Sentence2Neo4jTransformer.createGraphAuto(List(Knowledge("これはテストの前提3です。", "ja_JP", "{}")))
    val knowledgeSentenceSet = KnowledgeSentenceSet(
      List(Knowledge("これはテストの前提3です。","ja_JP", "{}")),
      List.empty[PropositionRelation],
      List(Knowledge("これはテストの主張3です。","ja_JP", "{}")),
      List.empty[PropositionRelation])
    Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSet)
    Sentence2Neo4jTransformer.createGraphAuto(List(Knowledge("これはテストの主張5です。", "ja_JP", "{}")))

  }

  override def afterAll(): Unit = {
    Neo4JAccessor.delete()
  }

  override implicit def defaultAwaitTimeout: Timeout = 60.seconds

  val controller: HomeController = inject[HomeController]
  "The specification1" should {
    "returns an appropriate response" in {

      val json = """{
                   |    "premise":[],
                   |    "claim":[{"sentence":"案ずるより産むが易し。","lang": "ja_JP", "extentInfoJson":"{}"}]
                   |}""".stripMargin

      val fr = FakeRequest(POST, "/analyze")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))

      val result = call(controller.analyze(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 0)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 1)

    }
  }

  "The specification2" should {
    "returns an appropriate response" in {

      val json = """{
                   |  "operator": "OR",
                   |  "knowledgeLeft": {
                   |    "operator": "AND",
                   |    "knowledgeLeft": {
                   |      "leaf": {
                   |        "premiseList": [
                   |          {
                   |            "sentence": "これはテストの前提1です。",
                   |            "lang": "ja_JP",
                   |            "extentInfoJson": "{}"
                   |          },
                   |          {
                   |            "sentence": "これはテストの前提2です。",
                   |            "lang": "ja_JP",
                   |            "extentInfoJson": "{}"
                   |          }
                   |        ],
                   |        "premiseLogicRelation": [
                   |          {
                   |            "operator": "AND",
                   |            "sourceIndex": 0,
                   |            "destinationIndex": 1
                   |          }
                   |        ],
                   |        "claimList": [
                   |          {
                   |            "sentence": "これはテストの主張1です。",
                   |            "lang": "ja_JP",
                   |            "extentInfoJson": "{}"
                   |          },
                   |          {
                   |            "sentence": "これはテストの主張2です。",
                   |            "lang": "ja_JP",
                   |            "extentInfoJson": "{}"
                   |          },
                   |          {
                   |            "sentence": "これはテストの主張3です。",
                   |            "lang": "ja_JP",
                   |            "extentInfoJson": "{}"
                   |          }
                   |        ],
                   |        "claimLogicRelation": [
                   |          {
                   |            "operator": "OR",
                   |            "sourceIndex": 0,
                   |            "destinationIndex": 1
                   |          },
                   |          {
                   |            "operator": "AND",
                   |            "sourceIndex": 1,
                   |            "destinationIndex": 2
                   |          },
                   |          {
                   |            "operator": "AND",
                   |            "sourceIndex": 0,
                   |            "destinationIndex": 2
                   |          }
                   |        ]
                   |      }
                   |    },
                   |    "knowledgeRight": {
                   |      "leaf": {
                   |        "premiseList": [
                   |          {
                   |            "sentence": "これはテストの前提3です。",
                   |            "lang": "ja_JP",
                   |            "extentInfoJson": "{}"
                   |          },
                   |          {
                   |            "sentence": "これはテストの前提4です。",
                   |            "lang": "ja_JP",
                   |            "extentInfoJson": "{}"
                   |          }
                   |        ],
                   |        "premiseLogicRelation": [
                   |          {
                   |            "operator": "AND",
                   |            "sourceIndex": 0,
                   |            "destinationIndex": 1
                   |          }
                   |        ],
                   |        "claimList": [
                   |          {
                   |            "sentence": "これはテストの主張4です。",
                   |            "lang": "ja_JP",
                   |            "extentInfoJson": "{}"
                   |          },
                   |          {
                   |            "sentence": "これはテストの主張5です。",
                   |            "lang": "ja_JP",
                   |            "extentInfoJson": "{}"
                   |          }
                   |        ],
                   |        "claimLogicRelation": [
                   |          {
                   |            "operator": "OR",
                   |            "sourceIndex": 0,
                   |            "destinationIndex": 1
                   |          }
                   |        ]
                   |      }
                   |    }
                   |  },
                   |  "knowledgeRight": {
                   |    "leaf": {
                   |      "premiseList": [
                   |        {
                   |          "sentence": "これはテストの前提5です。",
                   |          "lang": "ja_JP",
                   |          "extentInfoJson": "{}"
                   |        },
                   |        {
                   |          "sentence": "これはテストの前提6です。",
                   |          "lang": "ja_JP",
                   |          "extentInfoJson": "{}"
                   |        },
                   |        {
                   |          "sentence": "これはテストの前提7です。",
                   |          "lang": "ja_JP",
                   |          "extentInfoJson": "{}"
                   |        }
                   |
                   |      ],
                   |      "premiseLogicRelation": [
                   |        {
                   |          "operator": "AND",
                   |          "sourceIndex": 0,
                   |          "destinationIndex": 1
                   |        },
                   |         {
                   |          "operator": "AND",
                   |          "sourceIndex": 0,
                   |          "destinationIndex": 2
                   |        }
                   |      ],
                   |      "claimList": [
                   |        {
                   |          "sentence": "これはテストの主張6です。",
                   |          "lang": "ja_JP",
                   |          "extentInfoJson": "{}"
                   |        },
                   |        {
                   |          "sentence": "これはテストの主張7です。",
                   |          "lang": "ja_JP",
                   |          "extentInfoJson": "{}"
                   |        }
                   |      ],
                   |      "claimLogicRelation": [
                   |        {
                   |          "operator": "OR",
                   |          "sourceIndex": 0,
                   |          "destinationIndex": 1
                   |        }
                   |      ]
                   |    }
                   |  }
                   |}""".stripMargin

      val fr = FakeRequest(POST, "/analyzeKnowledgeTree")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))

      val result = call(controller.analyzeKnowledgeTree(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult = contentAsJson(result).toString()
      val flattenKnowledgeTree: FlattenedKnowledgeTree = Json.parse(jsonResult).as[FlattenedKnowledgeTree]
      assert(flattenKnowledgeTree.formula ==  "0 5 AND 9 OR")
      assert(flattenKnowledgeTree.subFormulaMap.get("0").get == "0 1 AND 2 3 OR 3 true AND AND 2 true AND AND IMP")
      assert(flattenKnowledgeTree.subFormulaMap.get("5").get == "true 6 AND 7 true OR IMP")
      assert(flattenKnowledgeTree.subFormulaMap.get("9").get == "9 10 AND 9 11 AND AND 12 13 OR IMP")
    }
  }


}

