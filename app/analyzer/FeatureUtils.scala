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
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseNode, KnowledgeBaseSemiGlobalNode, KnowledgeFeatureReference, LocalContext, LocalContextForFeature}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeForImage}
import com.ideal.linked.toposoid.protocol.model.base.AnalyzedSentenceObject
import com.ideal.linked.toposoid.protocol.model.parser.KnowledgeForParser
import play.api.libs.json.Json
import com.ideal.linked.toposoid.knowledgebase.regist.model.KnowledgeForTable
import com.ideal.linked.toposoid.knowledgebase.image.model.RegisteredImageContentResult
import com.ideal.linked.toposoid.knowledgebase.table.model.RegisteredTableContentResult

object FeatureUtils {
  /**
   *
   * @param asos
   * @param knowledgeForParsers
   * @return
   */
  def addFeatureInformation(asos: List[AnalyzedSentenceObject], knowledgeForParsers: List[KnowledgeForParser], transversalState:TransversalState): List[AnalyzedSentenceObject] = {
    asos.foldLeft(List.empty[AnalyzedSentenceObject]) {
      (acc, x) => {
        //Matching with sentenceId and linking image information
        val targetKnowledgeForParser = knowledgeForParsers.filter(_.sentenceId.equals(x.knowledgeBaseSemiGlobalNode.sentenceId)).head
        val knowledgeForImages = targetKnowledgeForParser.knowledge.knowledgeForImages
        val knowledgeForTables = targetKnowledgeForParser.knowledge.knowledgeForTables
        knowledgeForImages.size + knowledgeForTables.size match {
          case 0 => acc :+ x
          case _ => {
            val updateNodeMap = addLocalContextToNodeMap(x.nodeMap, knowledgeForImages, knowledgeForTables, transversalState)
            val updateSemiGlobalNode = addLocalContextForFeatureToSemiGlobalNode(x.knowledgeBaseSemiGlobalNode, knowledgeForImages, knowledgeForTables, transversalState)
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
  private def addLocalContextToNodeMap(nodeMap: Map[String, KnowledgeBaseNode], knowledgeForImages: List[KnowledgeForImage], knowledgeForTables: List[KnowledgeForTable], transversalState:TransversalState): Map[String, KnowledgeBaseNode] = {

    val targetImages = knowledgeForImages.filterNot(_.imageReference.reference.isWholeSentence)    
    val convertImages = targetImages.size match {
      case 0 => List.empty[KnowledgeForImage]
      case _ => targetImages.map(convertImage(_, transversalState))
    }
    val targetTables = knowledgeForTables.filterNot(_.tableReference.reference.isWholeSentence)
    val convertTables = targetTables.size match {
      case 0 => List.empty[KnowledgeForTable]
      case _ => targetTables.map(convertTable(_, transversalState))
    }

    convertImages.size + convertTables.size match {
      case 0 => nodeMap
      case _ => {
        nodeMap.map(x => {          
          val imageKnowledgeFeatureReferences = convertImages.foldLeft(List.empty[KnowledgeFeatureReference]) {
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

          val tableKnowledgeFeatureReferences = convertTables.foldLeft(List.empty[KnowledgeFeatureReference]) {
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
  private def addLocalContextForFeatureToSemiGlobalNode(knowledgeBaseSemiGlobalNode: KnowledgeBaseSemiGlobalNode, knowledgeForImages: List[KnowledgeForImage], knowledgeForTables: List[KnowledgeForTable], transversalState:TransversalState): KnowledgeBaseSemiGlobalNode = {
    val targetImages = knowledgeForImages.filter(_.imageReference.reference.isWholeSentence)
    val convertImages = targetImages.size match {
      case 0 => List.empty[KnowledgeForImage]
      case _ => targetImages.map(convertImage(_, transversalState))
    }    
    val targetTables = knowledgeForTables.filter(_.tableReference.reference.isWholeSentence)
    val convertTables = targetTables.size match {
      case 0 => List.empty[KnowledgeForTable]
      case _ => targetTables.map(convertTable(_, transversalState))
    }

    convertImages.size + targetTables.size match {
      case 0 => knowledgeBaseSemiGlobalNode
      case _ => {
        val imageKnowledgeFeatureReferences = convertImages.foldLeft(List.empty[KnowledgeFeatureReference]) {
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

        val tableKnowledgeFeatureReferences = convertTables.foldLeft(List.empty[KnowledgeFeatureReference]) {
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
    }


  }
    
  private def convertImage(knowledgeForImage: KnowledgeForImage, transversalState:TransversalState): KnowledgeForImage = {
    val resultJson = ToposoidUtils.callComponent(
        Json.toJson(knowledgeForImage).toString(),
        conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
        conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
        "convertImage",
        transversalState)
    val registeredImageContentResult = Json.parse(resultJson).as[RegisteredImageContentResult]
    if(registeredImageContentResult.statusInfo.status.equals("OK")){
      registeredImageContentResult.knowledgeForImage
    }else{
      throw Exception(registeredImageContentResult.statusInfo.message)
    }
  }
  private def convertTable(knowledgeForTable: KnowledgeForTable, transversalState:TransversalState): KnowledgeForTable = {
    val resultJson = ToposoidUtils.callComponent(
        Json.toJson(knowledgeForTable).toString(),
        conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
        conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
        "convertTable",
        transversalState)
    val registeredTableContentResult = Json.parse(resultJson).as[RegisteredTableContentResult]
    if(registeredTableContentResult.statusInfo.status.equals("OK")){
      registeredTableContentResult.knowledgeForTable
    }else{
      throw Exception(registeredTableContentResult.statusInfo.message)
    }
  }

}
