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
import com.ideal.linked.toposoid.protocol.model.frontend.AnalyzedEdges
import com.ideal.linked.toposoid.protocol.model.sat.FlattenedKnowledgeTree
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, defaultAwaitTimeout, status, _}
import play.api.test.{FakeRequest, _}

import scala.concurrent.duration.DurationInt

class HomeControllerSpecEnglish extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite with DefaultAwaitTimeout with Injecting{

  override def beforeAll(): Unit = {
    Neo4JAccessor.delete()
    Sentence2Neo4jTransformer.createGraphAuto(List(Knowledge("Life is so comfortable.","en_US", "{}", false)))
    Sentence2Neo4jTransformer.createGraphAuto(List(Knowledge("This is a premise3.", "en_US", "{}", false)))
    val knowledgeSentenceSet = KnowledgeSentenceSet(
      List(Knowledge("This is a premise3.","en_US", "{}", false)),
      List.empty[PropositionRelation],
      List(Knowledge("This is a claim3.","en_US", "{}", false)),
      List.empty[PropositionRelation])
    Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSet)
    Sentence2Neo4jTransformer.createGraphAuto(List(Knowledge("This is a claim5.", "en_US", "{}", false)))

  }

  override def afterAll(): Unit = {
    Neo4JAccessor.delete()
  }

  override implicit def defaultAwaitTimeout: Timeout = 600.seconds

  val controller: HomeController = inject[HomeController]
  "The specification1" should {

    "returns an appropriate response" in {

      val json = """{
                   |    "premise":[],
                   |    "claim":[{"sentence":"Life is so comfortable.","lang": "en_US", "extentInfoJson":"{}", "isNegativeSentence":false}]
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

  "The specification1-2" should {

    "returns an appropriate response" in {

      val json = """{
                   |    "premise":[{"sentence":"This is a premise3.","lang": "en_US", "extentInfoJson":"{}", "isNegativeSentence":false}],
                   |    "claim":[]
                   |}""".stripMargin

      val fr = FakeRequest(POST, "/analyze")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))

      val result = call(controller.analyze(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult = contentAsJson(result).toString()
      val analyzedSentenceObjects: AnalyzedSentenceObjects = Json.parse(jsonResult).as[AnalyzedSentenceObjects]
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("0").get.status).size == 1)
      assert(analyzedSentenceObjects.analyzedSentenceObjects.filter(_.deductionResultMap.get("1").get.status).size == 0)

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
                   |            "sentence": "This is a premise1.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false
                   |          },
                   |          {
                   |            "sentence": "This is a premise2.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false
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
                   |            "sentence": "This is a claim1.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false
                   |          },
                   |          {
                   |            "sentence": "This is a claim2.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false
                   |          },
                   |          {
                   |            "sentence": "This is a claim3.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false
                   |          }
                   |
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
                   |
                   |        ]
                   |      }
                   |    },
                   |    "knowledgeRight": {
                   |      "leaf": {
                   |        "premiseList": [
                   |          {
                   |            "sentence": "This is a premise3.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false
                   |          },
                   |          {
                   |            "sentence": "This is a premise4.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false
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
                   |            "sentence": "This is a claim4.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false
                   |          },
                   |          {
                   |            "sentence": "This is a claim5.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false
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
                   |          "sentence": "This is a premise5.",
                   |          "lang": "en_US",
                   |          "extentInfoJson": "{}",
                   |          "isNegativeSentence":false
                   |        },
                   |        {
                   |          "sentence": "This is a premise6.",
                   |          "lang": "en_US",
                   |          "extentInfoJson": "{}",
                   |          "isNegativeSentence":false
                   |        },
                   |        {
                   |          "sentence": "This is a premise7.",
                   |          "lang": "en_US",
                   |          "extentInfoJson": "{}",
                   |          "isNegativeSentence":false
                   |        }
                   |      ],
                   |      "premiseLogicRelation": [
                   |        {
                   |          "operator": "AND",
                   |          "sourceIndex": 0,
                   |          "destinationIndex": 1
                   |        },
                   |        {
                   |          "operator": "AND",
                   |          "sourceIndex": 0,
                   |          "destinationIndex": 2
                   |        }
                   |      ],
                   |      "claimList": [
                   |        {
                   |          "sentence": "This is a claim6.",
                   |          "lang": "en_US",
                   |          "extentInfoJson": "{}",
                   |          "isNegativeSentence":false
                   |        },
                   |        {
                   |          "sentence": "This is a claim7.",
                   |          "lang": "en_US",
                   |          "extentInfoJson": "{}",
                   |          "isNegativeSentence":false
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
      val analyzedEdges: AnalyzedEdges = Json.parse(jsonResult).as[AnalyzedEdges]

      analyzedEdges.analyzedEdges.map(x => println(x.source, x.target, x.value ))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a premise1 .", "OPTIMUM FOUND", "This is a claim1 .", "OPTIMUM FOUND", "IMP"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a claim1 .", "OPTIMUM FOUND", "This is a claim4 .", "OPTIMUM FOUND", "AND"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a claim1 .", "OPTIMUM FOUND", "This is a claim6 .", "OPTIMUM FOUND", "OR"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a premise1 .", "OPTIMUM FOUND", "This is a premise2 .", "OPTIMUM FOUND", "AND"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a claim1 .", "OPTIMUM FOUND", "This is a claim2 .", "OPTIMUM FOUND", "OR"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a claim2 .", "OPTIMUM FOUND", "This is a claim3 .", "TRIVIAL", "AND"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a claim1 .", "OPTIMUM FOUND", "This is a claim3 .", "TRIVIAL", "AND"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a premise3 .", "TRIVIAL", "This is a claim4 .", "OPTIMUM FOUND", "IMP"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a premise3 .", "TRIVIAL", "This is a premise4 .", "OPTIMUM FOUND", "AND"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a claim4 .", "OPTIMUM FOUND", "This is a claim5 .", "TRIVIAL", "OR"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a premise5 .", "OPTIMUM FOUND", "This is a claim6 .", "OPTIMUM FOUND", "IMP"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a premise5 .", "OPTIMUM FOUND", "This is a premise6 .", "OPTIMUM FOUND", "AND"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a premise5 .", "OPTIMUM FOUND", "This is a premise7 .", "OPTIMUM FOUND", "AND"))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a claim6 .", "OPTIMUM FOUND", "This is a claim7 .", "OPTIMUM FOUND", "OR"))

    }
  }

  "The specification3" should {
    "returns an appropriate response" in {

      val json = """{
                   |    "operator": "",
                   |    "knowledgeLeft": {
                   |        "leaf": {
                   |            "premiseList": [],
                   |            "premiseLogicRelation": [],
                   |            "claimList": [{
                   |            "sentence": "This is a claim1.",
                   |            "lang": "en_US",
                   |            "extentInfoJson": "{}",
                   |            "isNegativeSentence":false}],
                   |            "claimLogicRelation": []
                   |        }
                   |    },
                   |    "knowledgeRight": {
                   |        "leaf": {
                   |            "premiseList": [],
                   |            "premiseLogicRelation": [],
                   |            "claimList": [],
                   |            "claimLogicRelation": []
                   |        }
                   |    }
                   |}""".stripMargin

      val fr = FakeRequest(POST, "/analyzeKnowledgeTree")
        .withHeaders("Content-type" -> "application/json")
        .withJsonBody(Json.parse(json))

      val result = call(controller.analyzeKnowledgeTree(), fr)
      status(result) mustBe OK
      contentType(result) mustBe Some("application/json")
      val jsonResult = contentAsJson(result).toString()
      val analyzedEdges: AnalyzedEdges = Json.parse(jsonResult).as[AnalyzedEdges]

      analyzedEdges.analyzedEdges.map(x => println(x.source, x.target, x.value ))
      assert(checkAnalyzedEdges(analyzedEdges, "This is a claim1 .", "UNREASONABLE", "", "", ""))
    }
  }

  def checkAnalyzedEdges(actual:AnalyzedEdges, sentence1:String, status1:String, sentence2:String, status2:String, operator:String):Boolean = {
    actual.analyzedEdges.filter(x =>
      x.source.sentence.equals(sentence1) &&
        x.source.status.equals(status1) &&
        x.target.sentence.equals(sentence2) &&
        x.target.status.equals(status2) &&
        x.value.equals(operator)).size == 1
    //false
  }


}

