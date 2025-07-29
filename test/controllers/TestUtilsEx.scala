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

import com.ideal.linked.toposoid.common.{IMAGE, MANUAL, Neo4JUtilsImpl, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{ImageReference, Knowledge, KnowledgeForImage, PropositionRelation, Reference}
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.knowledgebase.featurevector.model.RegistContentResult
import com.ideal.linked.toposoid.knowledgebase.model.{KnowledgeBaseNode, KnowledgeBaseSemiGlobalNode, KnowledgeFeatureReference, LocalContext, LocalContextForFeature}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects}
import com.ideal.linked.toposoid.protocol.model.neo4j.Neo4jRecords
import com.ideal.linked.toposoid.protocol.model.parser.{KnowledgeForParser, KnowledgeSentenceSetForParser}
import com.ideal.linked.toposoid.test.utils.TestUtils

import play.api.libs.json.Json
import io.jvm.uuid.UUID

case class ImageBoxInfo(x:Int, y:Int, width:Int, height:Int)

object TestUtilsEx {

  val neo4JUtils = new Neo4JUtilsImpl()
  def deleteNeo4JAllData(transversalState: TransversalState): Unit = {
    val query = "MATCH (n) OPTIONAL MATCH (n)-[r]-() DELETE n,r"
    neo4JUtils.executeQuery(query, transversalState)
  }

  def executeQueryAndReturn(query: String, transversalState: TransversalState): Neo4jRecords = {
    neo4JUtils.executeQueryAndReturn(query, transversalState)
  }

  def registerSingleClaim(knowledgeForParser: KnowledgeForParser, transversalState: TransversalState): Unit = {
    val knowledgeSentenceSetForParser = KnowledgeSentenceSetForParser(
      List.empty[KnowledgeForParser],
      List.empty[PropositionRelation],
      List(knowledgeForParser),
      List.empty[PropositionRelation])
    TestUtils.registerData(knowledgeSentenceSetForParser, transversalState, addVectorFlag = true)
    Thread.sleep(5000)
  }

  var usedUuidList = List.empty[String]


  def getUUID(): String = {
    var uuid: String = UUID.random.toString
    while (usedUuidList.filter(_.equals(uuid)).size > 0) {
      uuid = UUID.random.toString
    }
    usedUuidList = usedUuidList :+ uuid
    uuid
  }


  def getKnowledge(lang:String, sentence: String, reference: Reference, imageBoxInfo: ImageBoxInfo, transversalState:TransversalState): Knowledge = {
    Knowledge(sentence, lang, "{}", false, List(getImageInfo(reference, imageBoxInfo, transversalState)))
  }

  def getImageInfo(reference: Reference, imageBoxInfo: ImageBoxInfo, transversalState:TransversalState): KnowledgeForImage = {
    val imageReference = ImageReference(reference: Reference, imageBoxInfo.x, imageBoxInfo.y, imageBoxInfo.width, imageBoxInfo.height)
    val knowledgeForImage = KnowledgeForImage(id = getUUID(), imageReference = imageReference)
    val registContentResultJson = ToposoidUtils.callComponent(
      Json.toJson(knowledgeForImage).toString(),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_HOST"),
      conf.getString("TOPOSOID_CONTENTS_ADMIN_PORT"),
      "registImage",
      transversalState
    )
    val registContentResult: RegistContentResult = Json.parse(registContentResultJson).as[RegistContentResult]
    registContentResult.knowledgeForImage
  }

  def addImageInfoToLocalNode(lang: String, inputSentence: String, knowledgeForImages: List[KnowledgeForImage], transversalState:TransversalState): AnalyzedSentenceObjects = {

    val json = lang match {
      case "ja_JP" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze", transversalState)
      case "en_US" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"), "analyze", transversalState)
    }
    //val json = ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze")
    val asos: AnalyzedSentenceObjects = Json.parse(json).as[AnalyzedSentenceObjects]
    val updatedAsos = asos.analyzedSentenceObjects.foldLeft(List.empty[AnalyzedSentenceObject]) {
      (acc, x) => {
        val nodeMap = x.nodeMap.foldLeft(Map.empty[String, KnowledgeBaseNode]) {
          (acc2, y) => {
            val compatibleImages = knowledgeForImages.filter(z => {
              z.imageReference.reference.surface == y._2.predicateArgumentStructure.surface && z.imageReference.reference.surfaceIndex == y._2.predicateArgumentStructure.currentId
            })
            val knowledgeFeatureReferences = compatibleImages.foldLeft(List.empty[KnowledgeFeatureReference]) {
              (acc3, z) => {
                acc3 :+ KnowledgeFeatureReference(
                  propositionId = y._2.propositionId,
                  sentenceId = y._2.sentenceId,
                  featureId = getUUID(),
                  featureType = IMAGE.index,
                  url = z.imageReference.reference.url,
                  source = z.imageReference.reference.originalUrlOrReference,
                  featureInputType = MANUAL.index,
                  extentText = "{}")
              }
            }
            val knowledgeBaseNode = KnowledgeBaseNode(
              nodeId = y._2.nodeId,
              propositionId = y._2.propositionId,
              sentenceId = y._2.sentenceId,
              predicateArgumentStructure = y._2.predicateArgumentStructure,
              localContext = LocalContext(
                lang = y._2.localContext.lang,
                namedEntity = y._2.localContext.namedEntity,
                rangeExpressions = y._2.localContext.rangeExpressions,
                categories = y._2.localContext.categories,
                domains = y._2.localContext.domains,
                knowledgeFeatureReferences = knowledgeFeatureReferences))
            acc2 ++ Map(y._1 -> knowledgeBaseNode)
          }
        }
        acc :+ AnalyzedSentenceObject(
          nodeMap = nodeMap,
          edgeList = x.edgeList,
          knowledgeBaseSemiGlobalNode = x.knowledgeBaseSemiGlobalNode,
          deductionResult = x.deductionResult)
      }
    }
    AnalyzedSentenceObjects(updatedAsos)
  }

  def addImageInfoToSemiGlobalNode(lang:String,inputSentence: String, knowledgeForImages: List[KnowledgeForImage], transversalState:TransversalState): AnalyzedSentenceObjects = {
    /**
     * CAUTION This function does not support cases where one node has multiple images!!!
     */
    val json = lang match {
      case "ja_JP" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze", transversalState)
      case "en_US" => ToposoidUtils.callComponent(inputSentence, conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"), "analyze", transversalState)
    }

    val asos: AnalyzedSentenceObjects = Json.parse(json).as[AnalyzedSentenceObjects]
    val updatedAsos = asos.analyzedSentenceObjects.foldLeft(List.empty[AnalyzedSentenceObject]) {
      (acc, x) => {

        val knowledgeForImage = knowledgeForImages(acc.size)

        val knowledgeFeatureReference = KnowledgeFeatureReference(
          propositionId = x.knowledgeBaseSemiGlobalNode.propositionId,
          sentenceId = x.knowledgeBaseSemiGlobalNode.sentenceId,
          featureId = getUUID(),
          featureType = IMAGE.index,
          url = knowledgeForImage.imageReference.reference.url,
          source = knowledgeForImage.imageReference.reference.originalUrlOrReference,
          featureInputType = MANUAL.index,
          extentText = "{}")

        val localContextForFeature = LocalContextForFeature(
          x.knowledgeBaseSemiGlobalNode.localContextForFeature.lang,
          List(knowledgeFeatureReference))

        val knowledgeBaseSemiGlobalNode = KnowledgeBaseSemiGlobalNode(
          sentenceId = x.knowledgeBaseSemiGlobalNode.sentenceId,
          propositionId = x.knowledgeBaseSemiGlobalNode.propositionId,
          documentId = x.knowledgeBaseSemiGlobalNode.documentId,
          sentence = x.knowledgeBaseSemiGlobalNode.sentence,
          sentenceType = x.knowledgeBaseSemiGlobalNode.sentenceType,
          localContextForFeature = localContextForFeature)


        acc :+ AnalyzedSentenceObject(
          nodeMap = x.nodeMap,
          edgeList = x.edgeList,
          knowledgeBaseSemiGlobalNode = knowledgeBaseSemiGlobalNode,
          deductionResult = x.deductionResult)
      }
    }
    AnalyzedSentenceObjects(updatedAsos)
  }
}
