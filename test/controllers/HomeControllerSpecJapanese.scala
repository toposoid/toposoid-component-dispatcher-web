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
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, Reference}
import com.ideal.linked.toposoid.protocol.model.frontend.AnalyzedEdges
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser}
import controllers.TestUtils.{getKnowledge, getUUID, registSingleClaim}
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

  before {
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema")
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "createSchema")
    Neo4JAccessor.delete()
    Thread.sleep(1000)
  }

  override def beforeAll(): Unit = {
    Neo4JAccessor.delete()
  }

  override def afterAll(): Unit = {
    Neo4JAccessor.delete()
  }

  override implicit def defaultAwaitTimeout: Timeout = 600.seconds

  val controller: HomeController = inject[HomeController]
  val lang = "ja_JP"

  "The specification1(nontrivial)" should {
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

      analyzedEdges.analyzedEdges.foreach(x => {
        if(!x.source.status.equals("")){
          assert(x.source.status.equals("OPTIMUM FOUND"))
        }
        if (!x.target.status.equals("")) {
          assert(x.target.status.equals("OPTIMUM FOUND"))
        }
      })

    }
  }

  "The specification2(exact-synonym--match-trivial)" should {
    "returns an appropriate response" in {

      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val sentenceA = "太郎は秀逸な発案をした。"
      val knowledge1 = Knowledge(sentenceA, "ja_JP", "{}", false)
      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))

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
          |                        "sentence": "太郎は秀逸な提案をした。",
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
          |                        "sentence": "太郎は秀逸な提案をした。",
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

      analyzedEdges.analyzedEdges.foreach(x => {
        if (!x.source.status.equals("")) {
          assert(x.source.status.equals("TRIVIAL"))
        }
        if (!x.target.status.equals("")) {
          assert(x.target.status.equals("TRIVIAL"))
        }
      })

    }
  }

  "The specification3(image-vector-match-trivial)" should {
    "returns an appropriate response" in {

      val sentenceA = "猫が２匹います。"
      val referenceA = Reference(url = "", surface = "猫が", surfaceIndex = 0, isWholeSentence = false,
        originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
      val imageBoxInfoA = ImageBoxInfo(x = 11, y = 11, width = 466, height = 310)
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false, List(imageA))
      val knowledge1 = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA)
      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))

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
          |                        "sentence": "ペットが２匹います。",
          |                        "lang": "ja_JP",
          |                        "extentInfoJson": "{}",
          |                        "isNegativeSentence": false,
          |                        "knowledgeForImages": [
          |                            {
          |                                "id": "225a0bc8-fabd-4a90-ad04-1247c32dc672",
          |                                "imageReference": {
          |                                    "reference": {
          |                                        "url": "",
          |                                        "surface": "ペットが",
          |                                        "surfaceIndex": 0,
          |                                        "isWholeSentence": false,
          |                                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg"
          |                                    },
          |                                    "x": 11,
          |                                    "y": 11,
          |                                    "width": 466,
          |                                    "height": 310
          |                                }
          |                            }
          |                        ]
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
          |                        "sentence": "ペットが２匹います。",
          |                        "lang": "ja_JP",
          |                        "extentInfoJson": "{}",
          |                        "isNegativeSentence": false,
          |                        "knowledgeForImages": [
          |                            {
          |                                "id": "225a0bc8-fabd-4a90-ad04-1247c32dc672",
          |                                "imageReference": {
          |                                    "reference": {
          |                                        "url": "",
          |                                        "surface": "ペットが",
          |                                        "surfaceIndex": 0,
          |                                        "isWholeSentence": false,
          |                                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg"
          |                                    },
          |                                    "x": 11,
          |                                    "y": 11,
          |                                    "width": 466,
          |                                    "height": 310
          |                                }
          |                            }
          |                        ]
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

      analyzedEdges.analyzedEdges.foreach(x => {
        if (!x.source.status.equals("")) {
          assert(x.source.status.equals("TRIVIAL"))
        }
        if (!x.target.status.equals("")) {
          assert(x.target.status.equals("TRIVIAL"))
        }
      })

    }
  }

  "The specification4(sentence-match-trivial)" should {
    "returns an appropriate response" in {

      val propositionId1 = UUID.random.toString
      val sentenceId1 = UUID.random.toString
      val sentenceA = "自然界の法則がすべての慣性系で同じように成り立っている。"
      val knowledge1 = Knowledge(sentenceA, "ja_JP", "{}", false)
      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))

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
          |                        "sentence": "自然界の物理法則は例外なくどの慣性系でも成立する。",
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
          |                        "sentence": "自然界の物理法則は例外なくどの慣性系でも成立する。",
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

      analyzedEdges.analyzedEdges.foreach(x => {
        if (!x.source.status.equals("")) {
          assert(x.source.status.equals("TRIVIAL"))
        }
        if (!x.target.status.equals("")) {
          assert(x.target.status.equals("TRIVIAL"))
        }
      })

    }
  }


  "The specification5(whole-sentence-image-feature-match-trivial)" should {
    "returns an appropriate response" in {

      val sentenceA = "猫が２匹います。"
      val referenceA = Reference(url = "", surface = "", surfaceIndex = -1, isWholeSentence = true,
        originalUrlOrReference = "http://images.cocodataset.org/val2017/000000039769.jpg")
      val imageBoxInfoA = ImageBoxInfo(x = 11, y = 11, width = 466, height = 310)
      val propositionId1 = getUUID()
      val sentenceId1 = getUUID()
      //val knowledge1 = Knowledge(sentenceA,"ja_JP", "{}", false, List(imageA))
      val knowledge1 = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA)
      registSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1))

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
          |                        "sentence": "ペットが２匹います。",
          |                        "lang": "ja_JP",
          |                        "extentInfoJson": "{}",
          |                        "isNegativeSentence": false,
          |                        "knowledgeForImages": [
          |                            {
          |                                "id": "225a0bc8-fabd-4a90-ad04-1247c32dc672",
          |                                "imageReference": {
          |                                    "reference": {
          |                                        "url": "",
          |                                        "surface": "",
          |                                        "surfaceIndex": -1,
          |                                        "isWholeSentence": true,
          |                                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg"
          |                                    },
          |                                    "x": 11,
          |                                    "y": 11,
          |                                    "width": 466,
          |                                    "height": 310
          |                                }
          |                            }
          |                        ]
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
          |                        "sentence": "ペットが２匹います。",
          |                        "lang": "ja_JP",
          |                        "extentInfoJson": "{}",
          |                        "isNegativeSentence": false,
          |                        "knowledgeForImages": [
          |                            {
          |                                "id": "225a0bc8-fabd-4a90-ad04-1247c32dc672",
          |                                "imageReference": {
          |                                    "reference": {
          |                                        "url": "",
          |                                        "surface": "",
          |                                        "surfaceIndex": -1,
          |                                        "isWholeSentence": true,
          |                                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg"
          |                                    },
          |                                    "x": 11,
          |                                    "y": 11,
          |                                    "width": 466,
          |                                    "height": 310
          |                                }
          |                            }
          |                        ]
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

      analyzedEdges.analyzedEdges.foreach(x => {
        if (!x.source.status.equals("")) {
          assert(x.source.status.equals("TRIVIAL"))
        }
        if (!x.target.status.equals("")) {
          assert(x.target.status.equals("TRIVIAL"))
        }
      })

    }
  }
}

