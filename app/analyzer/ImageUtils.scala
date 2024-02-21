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

package analyzer

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{IMAGE, MANUAL, ToposoidUtils}
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.RegistContentResult
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseNode, KnowledgeBaseSemiGlobalNode, KnowledgeFeatureReference, LocalContext, LocalContextForFeature}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeForImage}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObject
import com.ideal.linked.toposoid.protocol.model.parser.KnowledgeForParser
import play.api.libs.json.Json

object ImageUtils {
  /**
   *
   * @param asos
   * @param knowledgeForParsers
   * @return
   */
  def addImageInformation(asos: List[AnalyzedSentenceObject], knowledgeForParsers: List[KnowledgeForParser]): List[AnalyzedSentenceObject] = {
    //upload temporary images
    val updateKnowledgeForParsers = knowledgeForParsers.map(x => {
      x.knowledge.knowledgeForImages.size match {
        case 0 => x
        case _ => {
          val knowledgeForImages = x.knowledge.knowledgeForImages.map(uploadImage(_))
          val knowledge = Knowledge(x.knowledge.sentence, x.knowledge.lang, x.knowledge.extentInfoJson, x.knowledge.isNegativeSentence, knowledgeForImages)
          KnowledgeForParser(x.propositionId, x.sentenceId, knowledge)
        }
      }
    })

    asos.foldLeft(List.empty[AnalyzedSentenceObject]) {
      (acc, x) => {
        //Matching with sentenceId and linking image information
        val targetKnowledgeForParser = updateKnowledgeForParsers.filter(_.sentenceId.equals(x.knowledgeBaseSemiGlobalNode.sentenceId)).head
        val knowledgeForImages = targetKnowledgeForParser.knowledge.knowledgeForImages
        knowledgeForImages.size match {
          case 0 => acc :+ x
          case _ => {
            val updateNodeMap = addLocalContextToNodeMap(x.nodeMap, knowledgeForImages)
            val updateSemiGlobalNode = addLocalContextForFeatureToSemiGlobalNode(x.knowledgeBaseSemiGlobalNode, knowledgeForImages)
            acc :+ AnalyzedSentenceObject(updateNodeMap, x.edgeList, updateSemiGlobalNode, x.deductionResult)
          }
        }
      }
    }
  }

  /**
   *
   * @param nodeMap
   * @param knowledgeForImages
   * @return
   */
  private def addLocalContextToNodeMap(nodeMap: Map[String, KnowledgeBaseNode], knowledgeForImages: List[KnowledgeForImage]): Map[String, KnowledgeBaseNode] = {

    val targetImages = knowledgeForImages.filterNot(_.imageReference.reference.isWholeSentence)
    targetImages.size match {
      case 0 => nodeMap
      case _ => {
        nodeMap.map(x => {
          val updateKnowledgeFeatureReferences = targetImages.foldLeft(List.empty[KnowledgeFeatureReference]) {
            (acc, y) => {
              if (x._2.predicateArgumentStructure.surface.equals(y.imageReference.reference.surface) &&
                x._2.predicateArgumentStructure.currentId == y.imageReference.reference.surfaceIndex) {
                acc :+ KnowledgeFeatureReference(
                  propositionId = x._2.propositionId,
                  sentenceId = x._2.sentenceId,
                  featureId = y.id,
                  featureType = IMAGE.index,
                  url = y.imageReference.reference.url,
                  source = y.imageReference.reference.originalUrlOrReference,
                  featureInputType = MANUAL.index,
                  extentText = "{}")
              } else {
                acc
              }
            }
          }
          val localContext = x._2.localContext
          val updateLocalContext = LocalContext(
            lang = localContext.lang,
            namedEntity = localContext.namedEntity,
            rangeExpressions = localContext.rangeExpressions,
            categories = localContext.categories,
            domains = localContext.domains,
            knowledgeFeatureReferences = updateKnowledgeFeatureReferences)
          val updateKnowledgeBaseNode = KnowledgeBaseNode(
            nodeId = x._2.nodeId,
            propositionId = x._2.propositionId,
            sentenceId = x._2.sentenceId,
            predicateArgumentStructure = x._2.predicateArgumentStructure,
            localContext = updateLocalContext)
          (x._1, updateKnowledgeBaseNode)
        })
      }
    }
  }

  /**
   *
   * @param knowledgeBaseSemiGlobalNode
   * @param knowledgeForImages
   * @return
   */
  private def addLocalContextForFeatureToSemiGlobalNode(knowledgeBaseSemiGlobalNode: KnowledgeBaseSemiGlobalNode, knowledgeForImages: List[KnowledgeForImage]): KnowledgeBaseSemiGlobalNode = {
    val targetImages = knowledgeForImages.filter(_.imageReference.reference.isWholeSentence)
    targetImages.size match {
      case 0 => knowledgeBaseSemiGlobalNode
      case _ => {
        val updateKnowledgeFeatureReferences = targetImages.foldLeft(List.empty[KnowledgeFeatureReference]) {
          (acc, y) => {
              acc :+ KnowledgeFeatureReference(
                propositionId = knowledgeBaseSemiGlobalNode.propositionId,
                sentenceId = knowledgeBaseSemiGlobalNode.sentenceId,
                featureId =  y.id,
                featureType = IMAGE.index,
                url = y.imageReference.reference.url,
                source = y.imageReference.reference.originalUrlOrReference,
                featureInputType = MANUAL.index,
                extentText = "{}")
          }
        }
        val updateLocalContextForFeature = LocalContextForFeature(
          lang = knowledgeBaseSemiGlobalNode.localContextForFeature.lang,
          knowledgeFeatureReferences = updateKnowledgeFeatureReferences)

        KnowledgeBaseSemiGlobalNode(
          nodeId = knowledgeBaseSemiGlobalNode.nodeId,
          propositionId = knowledgeBaseSemiGlobalNode.propositionId,
          sentenceId = knowledgeBaseSemiGlobalNode.sentenceId,
          sentence = knowledgeBaseSemiGlobalNode.sentence,
          sentenceType = knowledgeBaseSemiGlobalNode.sentenceType,
          localContextForFeature = updateLocalContextForFeature)
      }
    }
  }

  /**
   *
   * @param knowledgeForImage
   * @return
   */
  private def uploadImage(knowledgeForImage: KnowledgeForImage): KnowledgeForImage = {
    //TODO TOPOSOID_CONTENTS_ADMIN_HOST APIインターフェース追加　＆　テンポラリファイル削除バッチの実装
    val registContentResultJson = ToposoidUtils.callComponent(
      Json.toJson(knowledgeForImage).toString(),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
      "uploadTemporaryImage")
    val registContentResult: RegistContentResult = Json.parse(registContentResultJson).as[RegistContentResult]
    registContentResult.knowledgeForImage
  }
}
