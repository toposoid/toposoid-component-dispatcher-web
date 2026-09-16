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

package analyzer

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{FeatureType, DataEntryType, ToposoidUtils, TransversalState}
//import com.ideal.linked.toposoid.knowledgebase.featurevector.model.RegistContentResult
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseNode, KnowledgeBaseSemiGlobalNode, KnowledgeFeatureReference, LocalContext, LocalContextForFeature}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeForImage}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObject
import com.ideal.linked.toposoid.protocol.model.parser.KnowledgeForParser
import play.api.libs.json.Json
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForTable

object FeatureUtils {
  /**
   *
   * @param asos
   * @param knowledgeForParsers
   * @return
   */
  def addFeatureInformation(asos: List[AnalyzedSentenceObject], knowledgeForParsers: List[KnowledgeForParser], transversalState:TransversalState): List[AnalyzedSentenceObject] = {
    //upload temporary images
    /*
    val updateKnowledgeForParsers = knowledgeForParsers.map(x => {
      x.knowledge.knowledgeForImages.size match {
        case 0 => x
        case _ => {
          val knowledgeForImages = x.knowledge.knowledgeForImages.map(uploadImage(_, transversalState))
          val knowledge = Knowledge(x.knowledge.sentence, x.knowledge.lang, x.knowledge.extentInfoJson, x.knowledge.isNegativeSentence, knowledgeForImages)
          KnowledgeForParser(x.propositionId, x.sentenceId, knowledge)
        }
      }
    })
    */
    asos.foldLeft(List.empty[AnalyzedSentenceObject]) {
      (acc, x) => {
        //Matching with sentenceId and linking image information
        val targetKnowledgeForParser = knowledgeForParsers.filter(_.sentenceId.equals(x.knowledgeBaseSemiGlobalNode.sentenceId)).head
        val knowledgeForImages = targetKnowledgeForParser.knowledge.knowledgeForImages
        val knowledgeForTables = targetKnowledgeForParser.knowledge.knowledgeForTables
        knowledgeForImages.size match {
          case 0 => acc :+ x
          case _ => {
            val updateNodeMap = addLocalContextToNodeMap(x.nodeMap, knowledgeForImages, knowledgeForTables)
            val updateSemiGlobalNode = addLocalContextForFeatureToSemiGlobalNode(x.knowledgeBaseSemiGlobalNode, knowledgeForImages, knowledgeForTables)
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
  private def addLocalContextToNodeMap(nodeMap: Map[String, KnowledgeBaseNode], knowledgeForImages: List[KnowledgeForImage], knowledgeForTables: List[KnowledgeForTable]): Map[String, KnowledgeBaseNode] = {

    val targetImages = knowledgeForImages.filterNot(_.imageReference.reference.isWholeSentence)
    targetImages.size match {
      case 0 => nodeMap
      case _ => {
        nodeMap.map(x => {
          val imageKnowledgeFeatureReferences = targetImages.foldLeft(List.empty[KnowledgeFeatureReference]) {
            (acc, y) => {
              if (x._2.predicateArgumentStructure.surface.equals(y.imageReference.reference.surface) &&
                x._2.predicateArgumentStructure.currentId == y.imageReference.reference.surfaceIndex) {
                acc :+ KnowledgeFeatureReference(
                  propositionId = x._2.propositionId,
                  sentenceId = x._2.sentenceId,
                  featureId = y.id,
                  featureType = FeatureType.IMAGE.index,
                  url = y.imageReference.reference.url,
                  source = y.imageReference.reference.originalUrlOrReference,
                  featureInputType = DataEntryType.MANUAL.index,
                  featureExtendedFields = Map.empty[String, String])
              } else {
                acc
              }
            }
          }

          val targetTables = knowledgeForTables.filterNot(_.tableReference.reference.isWholeSentence)
          val tableKnowledgeFeatureReferences = targetTables.foldLeft(List.empty[KnowledgeFeatureReference]) {
            (acc, y) => {
              if (x._2.predicateArgumentStructure.surface.equals(y.tableReference.reference.surface) &&
                x._2.predicateArgumentStructure.currentId == y.tableReference.reference.surfaceIndex) {
                acc :+ KnowledgeFeatureReference(
                  propositionId = x._2.propositionId,
                  sentenceId = x._2.sentenceId,
                  featureId = y.id,
                  featureType = FeatureType.TABLE.index,
                  url = y.tableReference.reference.url,
                  source = y.tableReference.reference.originalUrlOrReference,
                  featureInputType = DataEntryType.MANUAL.index,
                  featureExtendedFields = Map.empty[String, String])
              } else {
                acc
              }
            }
          }
          
          val localContext = x._2.localContext
          val updateLocalContext = LocalContext(
            lang = localContext.lang,
            namedEntities = localContext.namedEntities,
            rangeExpressions = localContext.rangeExpressions,
            categories = localContext.categories,
            domains = localContext.domains,
            knowledgeFeatureReferences = imageKnowledgeFeatureReferences ::: tableKnowledgeFeatureReferences,
            properNouns = localContext.properNouns
            )
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
  private def addLocalContextForFeatureToSemiGlobalNode(knowledgeBaseSemiGlobalNode: KnowledgeBaseSemiGlobalNode, knowledgeForImages: List[KnowledgeForImage], knowledgeForTables: List[KnowledgeForTable]): KnowledgeBaseSemiGlobalNode = {
    val targetImages = knowledgeForImages.filter(_.imageReference.reference.isWholeSentence)
    
    val imageKnowledgeFeatureReferences = targetImages.foldLeft(List.empty[KnowledgeFeatureReference]) {
          (acc, y) => {
              acc :+ KnowledgeFeatureReference(
                propositionId = knowledgeBaseSemiGlobalNode.propositionId,
                sentenceId = knowledgeBaseSemiGlobalNode.sentenceId,
                featureId =  y.id,
                featureType = FeatureType.IMAGE.index,
                url = y.imageReference.reference.url,
                source = y.imageReference.reference.originalUrlOrReference,
                featureInputType = DataEntryType.MANUAL.index,
                featureExtendedFields = Map.empty[String, String])
          }
        }

    val targetTables = knowledgeForTables.filter(_.tableReference.reference.isWholeSentence)

    val tableKnowledgeFeatureReferences = targetTables.foldLeft(List.empty[KnowledgeFeatureReference]) {
          (acc, y) => {
              acc :+ KnowledgeFeatureReference(
                propositionId = knowledgeBaseSemiGlobalNode.propositionId,
                sentenceId = knowledgeBaseSemiGlobalNode.sentenceId,
                featureId =  y.id,
                featureType = FeatureType.TABLE.index,
                url = y.tableReference.reference.url,
                source = y.tableReference.reference.originalUrlOrReference,
                featureInputType = DataEntryType.MANUAL.index,
                featureExtendedFields = Map.empty[String, String])
          }
        }
    
    

    val updateLocalContextForFeature = LocalContextForFeature(
          lang = knowledgeBaseSemiGlobalNode.localContextForFeature.lang,
          knowledgeFeatureReferences = imageKnowledgeFeatureReferences ::: tableKnowledgeFeatureReferences)

    KnowledgeBaseSemiGlobalNode(
        sentenceId = knowledgeBaseSemiGlobalNode.sentenceId,
        propositionId = knowledgeBaseSemiGlobalNode.propositionId,
        documentId = knowledgeBaseSemiGlobalNode.documentId,
        sentence = knowledgeBaseSemiGlobalNode.sentence,
        sentenceType = knowledgeBaseSemiGlobalNode.sentenceType,
        localContextForFeature = updateLocalContextForFeature)
  }
    
  

  /*
  private def uploadImage(knowledgeForImage: KnowledgeForImage, transversalState:TransversalState): KnowledgeForImage = {
    //TODO TOPOSOID_CONTENTS_ADMIN_HOST APIインターフェース追加　＆　テンポラリファイル削除バッチの実装
    val registContentResultJson = ToposoidUtils.callComponent(
      Json.toJson(knowledgeForImage).toString(),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
      "uploadTemporaryImage",
      transversalState)
    val registContentResult: RegistContentResult = Json.parse(registContentResultJson).as[RegistContentResult]
    registContentResult.knowledgeForImage
  }
  */
}
