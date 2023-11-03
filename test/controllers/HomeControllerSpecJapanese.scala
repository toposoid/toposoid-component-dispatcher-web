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
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.data.accessor.neo4j.Neo4JAccessor
import com.ideal.linked.toposoid.common.ToposoidUtils
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObjects
import com.ideal.linked.toposoid.protocol.model.frontend.AnalyzedEdges
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.sentence.transformer.neo4j.Sentence2Neo4jTransformer
import com.ideal.linked.toposoid.vectorizer.FeatureVectorizer
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, status, _}
import play.api.test.{FakeRequest, _}
import io.jvm.uuid.UUID

import scala.concurrent.duration.DurationInt

class HomeControllerSpecJapanese extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite  with DefaultAwaitTimeout with Injecting{

  override def beforeAll(): Unit = {
    Neo4JAccessor.delete()
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema")
    val propositionId1 = UUID.random.toString
    val sentenceId1 = UUID.random.toString
    val sentenceA = "案ずるより産むが易し。"
    val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false)
    registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))

    val propositionId2 = UUID.random.toString
    val sentenceId2 = UUID.random.toString
    val sentenceB = "自然界の法則がすべての慣性系で同じように成り立っている。"
    val knowledge2 = Knowledge(sentenceB,"ja_JP", "{}", false)
    registSingleClaim(KnowledgeForParser(propositionId2, sentenceId2, knowledge2))

  }

  override def afterAll(): Unit = {
    Neo4JAccessor.delete()
  }

  //override implicit def defaultAwaitTimeout: Timeout = 120.seconds
  override implicit def defaultAwaitTimeout: Timeout = 600.seconds

  def registSingleClaim(knowledgeForParser:KnowledgeForParser): Unit = {
    val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
      List.empty[KnowledgeForParser],
      List.empty[PropositionRelation],
      List(knowledgeForParser),
      List.empty[PropositionRelation])
    Sentence2Neo4jTransformer.createGraph(knowledgeSentenceSetForParser)
    FeatureVectorizer.createVector(knowledgeSentenceSetForParser)
    Thread.sleep(5000)

  }

  val controller: HomeController = inject[HomeController]

  "The specification1" should {
    "returns an appropriate response" in {

      val json = """{
                   |    "premise":[],
                   |    "claim":[{"sentence":"案ずるより産むが易し。","lang": "ja_JP", "extentInfoJson":"{}", "isNegativeSentence":false, "knowledgeForImages":[]}]
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
                   |    "premise":[],
                   |    "claim":[{"sentence":"自然界の物理法則は例外なくどの慣性系でも成立する。","lang": "ja_JP", "extentInfoJson":"{}", "isNegativeSentence":false, "knowledgeForImages":[]}]
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

  "The specification3" should {
    "returns an appropriate response" in {

      val json = """{
                   |    "regulation": {
                   |        "operator": "OR",
                   |        "knowledgeLeft": {
                   |            "operator": "OR",
                   |            "knowledgeLeft": {
                   |                "leaf": {
                   |                    "premiseList": [],
                   |                    "premiseLogicRelation": [],
                   |                    "claimList": [
                   |                        {
                   |                            "sentence": "Aは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[]
                   |                        },
                   |                        {
                   |                            "sentence": "Bは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[]
                   |                        },
                   |                        {
                   |                            "sentence": "Cは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": true,
                   |                            "knowledgeForImages":[]
                   |                        }
                   |                    ],
                   |                    "claimLogicRelation": [
                   |                        {
                   |                            "operator": "AND",
                   |                            "sourceIndex": 0,
                   |                            "destinationIndex": 1
                   |                        },
                   |                        {
                   |                            "operator": "AND",
                   |                            "sourceIndex": 1,
                   |                            "destinationIndex": 2
                   |                        }
                   |                    ]
                   |                }
                   |            },
                   |            "knowledgeRight": {
                   |                "leaf": {
                   |                    "premiseList": [],
                   |                    "premiseLogicRelation": [],
                   |                    "claimList": [
                   |                        {
                   |                            "sentence": "Aは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[]
                   |                        },
                   |                        {
                   |                            "sentence": "Bは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": true,
                   |                            "knowledgeForImages":[]
                   |                        },
                   |                        {
                   |                            "sentence": "Cは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[]
                   |                        }
                   |                    ],
                   |                    "claimLogicRelation": [
                   |                        {
                   |                            "operator": "AND",
                   |                            "sourceIndex": 0,
                   |                            "destinationIndex": 1
                   |                        },
                   |                        {
                   |                            "operator": "AND",
                   |                            "sourceIndex": 1,
                   |                            "destinationIndex": 2
                   |                        }
                   |                    ]
                   |                }
                   |            }
                   |        },
                   |        "knowledgeRight": {
                   |            "leaf": {
                   |                "premiseList": [],
                   |                "premiseLogicRelation": [],
                   |                "claimList": [
                   |                    {
                   |                        "sentence": "Aは正直者である。",
                   |                        "lang": "ja_JP",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": true,
                   |                        "knowledgeForImages":[]
                   |                    },
                   |                    {
                   |                        "sentence": "Bは正直者である。",
                   |                        "lang": "ja_JP",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": false,
                   |                        "knowledgeForImages":[]
                   |                    },
                   |                    {
                   |                        "sentence": "Cは正直者である。",
                   |                        "lang": "ja_JP",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": false,
                   |                        "knowledgeForImages":[]
                   |                    }
                   |                ],
                   |                "claimLogicRelation": [
                   |                    {
                   |                        "operator": "AND",
                   |                        "sourceIndex": 0,
                   |                        "destinationIndex": 1
                   |                    },
                   |                    {
                   |                        "operator": "AND",
                   |                        "sourceIndex": 1,
                   |                        "destinationIndex": 2
                   |                    }
                   |                ]
                   |            }
                   |        }
                   |    },
                   |    "hypothesis": {
                   |        "operator": "AND",
                   |        "knowledgeLeft": {
                   |            "operator": "AND",
                   |            "knowledgeLeft": {
                   |                "leaf": {
                   |                    "premiseList": [
                   |                        {
                   |                            "sentence": "Aは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[]
                   |                        }
                   |                    ],
                   |                    "premiseLogicRelation": [],
                   |                    "claimList": [
                   |                        {
                   |                            "sentence": "Cは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": true,
                   |                            "knowledgeForImages":[]
                   |                        }
                   |                    ],
                   |                    "claimLogicRelation": []
                   |                }
                   |            },
                   |            "knowledgeRight": {
                   |                "leaf": {
                   |                    "premiseList": [
                   |                        {
                   |                            "sentence": "Bは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[]
                   |                        }
                   |                    ],
                   |                    "premiseLogicRelation": [],
                   |                    "claimList": [
                   |                        {
                   |                            "sentence": "Aは正直者である。",
                   |                            "lang": "ja_JP",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[]
                   |                        }
                   |                    ],
                   |                    "claimLogicRelation": []
                   |                }
                   |            }
                   |        },
                   |        "knowledgeRight": {
                   |            "leaf": {
                   |                "premiseList": [
                   |                    {
                   |                        "sentence": "Cは正直者である。",
                   |                        "lang": "ja_JP",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": false,
                   |                        "knowledgeForImages":[]
                   |                    }
                   |                ],
                   |                "premiseLogicRelation": [],
                   |                "claimList": [
                   |                    {
                   |                        "sentence": "Bは正直者である。",
                   |                        "lang": "ja_JP",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": true,
                   |                        "knowledgeForImages":[]
                   |                    }
                   |                ],
                   |                "claimLogicRelation": []
                   |            }
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

    }
  }

  "The specification4" should {
    "returns an appropriate response" in {

      val json = """{
                   |    "regulation": {
                   |        "knowledgeLeft": {
                   |            "leaf": {
                   |                "premiseList": [],
                   |                "premiseLogicRelation": [],
                   |                "claimList": [],
                   |                "claimLogicRelation": []
                   |            }
                   |        },
                   |        "operator": "",
                   |        "knowledgeRight": {
                   |            "leaf": {
                   |                "premiseList": [],
                   |                "premiseLogicRelation": [],
                   |                "claimList": [
                   |                    {
                   |                        "sentence": "これは主張1です。",
                   |                        "lang": "ja_JP",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": false,
                   |                        "knowledgeForImages":[]
                   |                    }
                   |                ],
                   |                "claimLogicRelation": []
                   |            }
                   |        }
                   |    },
                   |    "hypothesis": {
                   |        "knowledgeLeft": {
                   |            "leaf": {
                   |                "premiseList": [],
                   |                "premiseLogicRelation": [],
                   |                "claimList": [],
                   |                "claimLogicRelation": []
                   |            }
                   |        },
                   |        "operator": "",
                   |        "knowledgeRight": {
                   |            "leaf": {
                   |                "premiseList": [],
                   |                "premiseLogicRelation": [],
                   |                "claimList": [
                   |                    {
                   |                        "sentence": "これは主張1です。",
                   |                        "lang": "ja_JP",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": false,
                   |                        "knowledgeForImages":[]
                   |                    }
                   |                ],
                   |                "claimLogicRelation": []
                   |            }
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

    }

    "The specification5" should {
      "returns an appropriate response" in {

        val json =
          """{
            |    "regulation": {
            |        "knowledgeLeft": {
            |            "leaf": {
            |                "premiseList": [],
            |                "premiseLogicRelation": [],
            |                "claimList": [],
            |                "claimLogicRelation": []
            |            }
            |        },
            |        "operator": "",
            |        "knowledgeRight": {
            |            "leaf": {
            |                "premiseList": [],
            |                "premiseLogicRelation": [],
            |                "claimList": [
            |                    {
            |                        "sentence": "全ての人は自分の運命を自分で決められる",
            |                        "lang": "ja_JP",
            |                        "extentInfoJson": "{}",
            |                        "isNegativeSentence": false,
            |                        "knowledgeForImages":[]
            |                    },
            |                    {
            |                        "sentence": "人生は自分でつくるもの",
            |                        "lang": "ja_JP",
            |                        "extentInfoJson": "{}",
            |                        "isNegativeSentence": false,
            |                        "knowledgeForImages":[]
            |                    }
            |                ],
            |                "claimLogicRelation": [
            |                    {
            |                        "operator": "AND",
            |                        "sourceIndex": 0,
            |                        "destinationIndex": 1
            |                    }
            |                ]
            |            }
            |        }
            |    },
            |    "hypothesis": {
            |        "knowledgeLeft": {
            |            "leaf": {
            |                "premiseList": [],
            |                "premiseLogicRelation": [],
            |                "claimList": [],
            |                "claimLogicRelation": []
            |            }
            |        },
            |        "operator": "",
            |        "knowledgeRight": {
            |            "leaf": {
            |                "premiseList": [],
            |                "premiseLogicRelation": [],
            |                "claimList": [
            |                    {
            |                        "sentence": "全ての人は自分の運命を自分で決められる",
            |                        "lang": "ja_JP",
            |                        "extentInfoJson": "{}",
            |                        "isNegativeSentence": false,
            |                        "knowledgeForImages":[]
            |                    },
            |                    {
            |                        "sentence": "人生は自分でつくるもの",
            |                        "lang": "ja_JP",
            |                        "extentInfoJson": "{}",
            |                        "isNegativeSentence": false,
            |                        "knowledgeForImages":[]
            |                    }
            |                ],
            |                "claimLogicRelation": [
            |                    {
            |                        "operator": "AND",
            |                        "sourceIndex": 0,
            |                        "destinationIndex": 1
            |                    }
            |                ]
            |            }
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

        analyzedEdges.analyzedEdges.map(x => println(x.source, x.target, x.value))

      }
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

