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

import akka.util.Timeout
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{TRANSVERSAL_STATE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, Reference}
import com.ideal.linked.toposoid.protocol.model.frontend.{AnalyzedEdges, Endpoint}
import com.ideal.linked.toposoid.protocol.model.parser.KnowledgeForParser
import controllers.TestUtilsEx.{executeQueryAndReturn, getKnowledge, getUUID, registerSingleClaim}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.{POST, contentType, status, _}
import play.api.test._
import io.jvm.uuid.UUID

import scala.concurrent.duration.DurationInt

class HomeControllerSpecJapanese extends PlaySpec with BeforeAndAfter with BeforeAndAfterAll with GuiceOneAppPerSuite  with DefaultAwaitTimeout with Injecting{

  val transversalState:TransversalState = TransversalState(userId="test-user", username="guest", roleId=0, csrfToken = "")
  val transversalStateJson:String = Json.toJson(transversalState).toString()

  before {
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_SENTENCE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    ToposoidUtils.callComponent("{}", conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_HOST"), conf.getString("TOPOSOID_IMAGE_VECTORDB_ACCESSOR_PORT"), "createSchema", transversalState)
    TestUtilsEx.deleteNeo4JAllData(transversalState)
    Thread.sleep(1000)
  }

  override def beforeAll(): Unit = {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
  }

  override def afterAll(): Unit = {
    TestUtilsEx.deleteNeo4JAllData(transversalState)
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
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |
                   |                        },
                   |                        {
                   |                            "sentence": "Bは正直者である。",
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |
                   |                        },
                   |                        {
                   |                            "sentence": "Cは正直者である。",
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": true,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
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
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |                        },
                   |                        {
                   |                            "sentence": "Bは正直者である。",
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": true,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |                        },
                   |                        {
                   |                            "sentence": "Cは正直者である。",
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
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
                   |                        "lang": "",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": true,
                   |                        "knowledgeForImages":[],
                   |                        "knowledgeForTables": [],
                   |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |
                   |                    },
                   |                    {
                   |                        "sentence": "Bは正直者である。",
                   |                        "lang": "",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": false,
                   |                        "knowledgeForImages":[],
                   |                        "knowledgeForTables": [],
                   |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |                    },
                   |                    {
                   |                        "sentence": "Cは正直者である。",
                   |                        "lang": "",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": false,
                   |                        "knowledgeForImages":[],
                   |                        "knowledgeForTables": [],
                   |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
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
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |                        }
                   |                    ],
                   |                    "premiseLogicRelation": [],
                   |                    "claimList": [
                   |                        {
                   |                            "sentence": "Cは正直者である。",
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": true,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
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
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |                        }
                   |                    ],
                   |                    "premiseLogicRelation": [],
                   |                    "claimList": [
                   |                        {
                   |                            "sentence": "Aは正直者である。",
                   |                            "lang": "",
                   |                            "extentInfoJson": "{}",
                   |                            "isNegativeSentence": false,
                   |                            "knowledgeForImages":[],
                   |                            "knowledgeForTables": [],
                   |                            "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                            "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
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
                   |                        "lang": "",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": false,
                   |                        "knowledgeForImages":[],
                   |                        "knowledgeForTables": [],
                   |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |                    }
                   |                ],
                   |                "premiseLogicRelation": [],
                   |                "claimList": [
                   |                    {
                   |                        "sentence": "Bは正直者である。",
                   |                        "lang": "",
                   |                        "extentInfoJson": "{}",
                   |                        "isNegativeSentence": true,
                   |                        "knowledgeForImages":[],
                   |                        "knowledgeForTables": [],
                   |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
                   |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
                   |
                   |                    }
                   |                ],
                   |                "claimLogicRelation": []
                   |            }
                   |        }
                   |    }
                   |}""".stripMargin

      val fr = FakeRequest(POST, "/analyzeKnowledgeTree")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)

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
          |                        "lang": "",
          |                        "extentInfoJson": "{}",
          |                        "isNegativeSentence": false,
          |                        "knowledgeForImages":[],
          |                        "knowledgeForTables": [],
          |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
          |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
          |                   }
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
          |                        "lang": "",
          |                        "extentInfoJson": "{}",
          |                        "isNegativeSentence": false,
          |                        "knowledgeForImages":[],
          |                        "knowledgeForTables": [],
          |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
          |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
          |                    }
          |                ],
          |                "claimLogicRelation": []
          |            }
          |        }
          |    }
          |}""".stripMargin

      val fr = FakeRequest(POST, "/analyzeKnowledgeTree")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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
      val knowledge1 = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA, transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)

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
          |                        "lang": "",
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
          |                                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg",
          |                                        "metaInformations": []
          |                                    },
          |                                    "x": 11,
          |                                    "y": 11,
          |                                    "width": 466,
          |                                    "height": 310
          |                                }
          |                            }
          |                        ],
          |                        "knowledgeForTables": [],
          |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
          |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
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
          |                        "lang": "",
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
          |                                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg",
          |                                        "metaInformations": []
          |                                    },
          |                                    "x": 11,
          |                                    "y": 11,
          |                                    "width": 466,
          |                                    "height": 310
          |                                }
          |                            }
          |                        ],
          |                        "knowledgeForTables": [],
          |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
          |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
          |                    }
          |                ],
          |                "claimLogicRelation": []
          |            }
          |        }
          |    }
          |}""".stripMargin

      val fr = FakeRequest(POST, "/analyzeKnowledgeTree")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)

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
          |                        "lang": "",
          |                        "extentInfoJson": "{}",
          |                        "isNegativeSentence": false,
          |                        "knowledgeForImages":[],
          |                        "knowledgeForTables": [],
          |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
          |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
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
          |                        "lang": "",
          |                        "extentInfoJson": "{}",
          |                        "isNegativeSentence": false,
          |                        "knowledgeForImages":[],
          |                        "knowledgeForTables": [],
          |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
          |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
          |                    }
          |                ],
          |                "claimLogicRelation": []
          |            }
          |        }
          |    }
          |}""".stripMargin

      val fr = FakeRequest(POST, "/analyzeKnowledgeTree")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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
      val knowledge1 = getKnowledge(lang = lang, sentence = sentenceA, reference = referenceA, imageBoxInfo = imageBoxInfoA, transversalState)
      registerSingleClaim(KnowledgeForParser(propositionId1, sentenceId1, knowledge1), transversalState)

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
          |                        "lang": "",
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
          |                                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg",
          |                                        "metaInformations": []
          |                                    },
          |                                    "x": 11,
          |                                    "y": 11,
          |                                    "width": 466,
          |                                    "height": 310
          |                                }
          |                            }
          |                        ],
          |                        "knowledgeForTables": [],
          |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
          |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
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
          |                        "lang": "",
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
          |                                        "originalUrlOrReference": "http://images.cocodataset.org/val2017/000000039769.jpg",
          |                                        "metaInformations": []
          |                                    },
          |                                    "x": 11,
          |                                    "y": 11,
          |                                    "width": 466,
          |                                    "height": 310
          |                                }
          |                            }
          |                        ],
          |                        "knowledgeForTables": [],
          |                        "knowledgeForDocument": {"id":"", "filename":"", "url":"", "titleOfTopPage": ""},
          |                        "documentPageReference": {"pageNo":-1, "references":[], "tableOfContents":[], "headlines":[]}
          |                    }
          |                ],
          |                "claimLogicRelation": []
          |            }
          |        }
          |    }
          |}""".stripMargin

      val fr = FakeRequest(POST, "/analyzeKnowledgeTree")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
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


  "The configuration that there are init deduction-units." should {
    "returns an appropriate response" in {


      val fr2 = FakeRequest(POST, "/getEndPoints")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse("{}"))

      val result2 = call(controller.getEndPointsFromInMemoryDB(), fr2)
      status(result2) mustBe OK
      contentType(result2) mustBe Some("application/json")

      val jsonResult = contentAsJson(result2).toString()
      val endPoints = Json.parse(jsonResult).as[Seq[Endpoint]]

      assert(endPoints.size == 5)
      val defaultEndPoints: Seq[Endpoint] = Seq(
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT1_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT1_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT1_NAME")),
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT2_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT2_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT2_NAME")),
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT3_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT3_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT3_NAME")),
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT4_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT4_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT4_NAME")),
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT5_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT5_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT5_NAME"))
      )

      endPoints.zip(defaultEndPoints).foreach(x => {
        assert(x._1 == x._2)
      })
    }
  }

    "The configuration that there are no deduction-units." should {
    "returns an appropriate response" in {
      /*
      for (index <- 0 to 4) {
        val json =
          """{
            |    "index": %d,
            |    "function":{
            |        "host": "%s",
            |        "port": "%s"
            |    }
            |}""".stripMargin.format(index, "-", "-")

        val fr1 = FakeRequest(POST, "/changeEndPoints")
          .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
          .withJsonBody(Json.parse(json))

        val result1 = call(controller.changeEndPoints(), fr1)
        status(result1) mustBe OK
        contentType(result1) mustBe Some("application/json")
        assert(contentAsJson(result1).toString().equals("""{"status":"OK"}"""))
      }
      */
      val emptyEndPoints = Seq(Endpoint("-", "-", ""), Endpoint("-", "-", ""), Endpoint("-", "-", ""), Endpoint("-", "-", ""), Endpoint("-", "-", ""))
      val fr1 = FakeRequest(POST, "/changeEndPoints")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.toJson(emptyEndPoints))

      val result1 = call(controller.changeEndPoints(), fr1)
      status(result1) mustBe OK
      contentType(result1) mustBe Some("application/json")
      assert(contentAsJson(result1).toString().equals("""{"status":"OK"}"""))


      val fr2 = FakeRequest(POST, "/getEndPoints")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.parse("{}"))

      val result2 = call(controller.getEndPointsFromInMemoryDB(), fr2)
      status(result2) mustBe OK
      contentType(result2) mustBe Some("application/json")

      val jsonResult = contentAsJson(result2).toString()
      val endPoints = Json.parse(jsonResult).as[Seq[Endpoint]]
      assert(endPoints.filter(x => x.host.equals("-") && x.port.equals("-")).size == 5)

      val defaultEndPoints: Seq[Endpoint] = Seq(
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT1_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT1_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT1_NAME")),
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT2_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT2_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT2_NAME")),
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT3_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT3_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT3_NAME")),
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT4_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT4_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT4_NAME")),
        Endpoint(conf.getString("TOPOSOID_DEDUCTION_UNIT5_HOST"), port = conf.getString("TOPOSOID_DEDUCTION_UNIT5_PORT"), name = conf.getString("TOPOSOID_DEDUCTION_UNIT5_NAME")))

      val fr3 = FakeRequest(POST, "/changeEndPoints")
        .withHeaders("Content-type" -> "application/json", TRANSVERSAL_STATE.str -> transversalStateJson)
        .withJsonBody(Json.toJson(defaultEndPoints))

      val result3 = call(controller.changeEndPoints(), fr3)
      status(result3) mustBe OK

    }
  }

}

