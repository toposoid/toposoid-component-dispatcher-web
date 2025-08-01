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

import analyzer.ImageUtils.addImageInformation
import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{CLAIM, PREMISE, ToposoidUtils, TransversalState}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, DeductionResult}
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentenceForParser, KnowledgeForParser, KnowledgeLeaf, KnowledgeNode, KnowledgeTree}
import controllers.{ParsedKnowledgeTree, SentenceInfo}
import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}
import io.jvm.uuid.UUID

class RequestAnalyzer {


  /**
   * This function performs the assignment of propositions whose truth is obvious from the knowledge graph
   * @param analyzedSentenceObjects
   * @param sentenceMapForSat
   * @param subFormulaMap
   * @return
   */
  def assignTrivialProposition(analyzedSentenceObjects:List[AnalyzedSentenceObject], sentenceMapForSat:Map[String, SentenceInfo], subFormulaMap:Map[String, String]): (Map[String, String], Map[String, Option[DeductionResult]])  ={
    val hasPremise = analyzedSentenceObjects.filter(x => x.knowledgeBaseSemiGlobalNode.sentenceType == PREMISE.index).size > 0
    //This is a list of PropositionIds that can be found to be true or false as a result of searching GraphDB.
    val trivialPropositionIds:Map[String,Option[DeductionResult]] =
      analyzedSentenceObjects.foldLeft(Map.empty[String, Option[DeductionResult]]){
        (acc, x) =>
          acc ++ extractDeductionResult(x)
      }
    //This is converting the positionId to satId
    val trivialSatIds = trivialPropositionIds.foldLeft(Map.empty[String, Boolean]){
      (acc, x) => x._2 match {
        case Some(_) => {
          hasPremise match {
            case true => acc ++ Map(sentenceMapForSat.get(x._1).get.satId -> (x._2.get.status && x._2.get.havePremiseInGivenProposition))
            case _  => acc ++ Map(sentenceMapForSat.get(x._1).get.satId -> x._2.get.status)
          }
        }
        case _ => acc
      }
    }
    //This is the assignment of trivial propositional truth values to formula
    val resultMap:Map[String, String] = subFormulaMap.foldLeft(Map.empty[String, String]){
      (acc, x) =>
        acc ++ Map(x._1 -> assignment(x._2, trivialSatIds))
    }
    (resultMap, trivialPropositionIds.filterNot(_._2 == None))
  }

  /**
   * True/False assignment
   * @param subFormula
   * @param trivialSatIds
   * @return
   */
  private def assignment(subFormula:String, trivialSatIds:Map[String, Boolean]): String ={
    subFormula.split(" ").map(x => trivialSatIds.isDefinedAt(x) match  {
      case true => trivialSatIds.get(x).get
      case false => x
    }).mkString(" ")
  }

  /**
   * This function extracts a trivial proposition as a result of searching GraphDB.
   * @param analyzedSentenceObject
   * @return
   */
  private def extractDeductionResult(analyzedSentenceObject:AnalyzedSentenceObject): Map[String, Option[DeductionResult]] ={
    //An analyzedSentenceObject always has one propositionId.
    val propositionId = analyzedSentenceObject.nodeMap.map(_._2.propositionId).head

    //An analyzedSentenceObject has GraphDB search results of either Premis or Claim depending on the sentenceType.
    val deductionResult:DeductionResult = analyzedSentenceObject.deductionResult
    val status:Option[DeductionResult] = deductionResult.status match {
      case true => Some(deductionResult)
      case _ => None
    }
    Map(propositionId -> status)
  }

  private def preprocess(rawKnowledgeSentenceSet: KnowledgeSentenceSet, transversalState: TransversalState): KnowledgeSentenceSet = {

    val resKnowledgeSentenceSet: String = ToposoidUtils.callComponent(Json.toJson(rawKnowledgeSentenceSet).toString(), conf.getString("TOPOSOID_LANGUAGE_DETECTOR_HOST"), conf.getString("TOPOSOID_LANGUAGE_DETECTOR_PORT"), "detectLanguages", transversalState)
    val knowledgeSentenceSet = Json.parse(resKnowledgeSentenceSet).as[KnowledgeSentenceSet]
    KnowledgeSentenceSet(
      premiseList = ToposoidUtils.preprocessForSentence(knowledgeSentenceSet.premiseList),
      premiseLogicRelation = knowledgeSentenceSet.premiseLogicRelation,
      claimList = ToposoidUtils.preprocessForSentence(knowledgeSentenceSet.claimList),
      claimLogicRelation = knowledgeSentenceSet.claimLogicRelation
    )
  }
  /**
   * This function delegates the processing of a given sentence to passer for each multilingual.
   * @param knowledgeSentenceSet
   * @return
   */
  def parseKnowledgeSentence(noLangKnowledgeSentenceSet: KnowledgeSentenceSet, transversalState:TransversalState):List[AnalyzedSentenceObject] = Try{

    val knowledgeSentenceSet = preprocess(noLangKnowledgeSentenceSet, transversalState)
    val premiseJapanese:List[KnowledgeForParser] = knowledgeSentenceSet.premiseList.filter(_.lang == "ja_JP").map(KnowledgeForParser(UUID.random.toString, UUID.random.toString, _))
    val claimJapanese:List[KnowledgeForParser] = knowledgeSentenceSet.claimList.filter(_.lang == "ja_JP").map(KnowledgeForParser(UUID.random.toString, UUID.random.toString, _))
    val premiseEnglish:List[KnowledgeForParser] = knowledgeSentenceSet.premiseList.filter(_.lang.startsWith("en_")).map(KnowledgeForParser(UUID.random.toString, UUID.random.toString, _))
    val claimEnglish:List[KnowledgeForParser] = knowledgeSentenceSet.claimList.filter(_.lang.startsWith("en_")).map(KnowledgeForParser(UUID.random.toString, UUID.random.toString, _))
    val japaneseInputSentences:String = Json.toJson(InputSentenceForParser(premiseJapanese, claimJapanese)).toString()
    val englishInputSentences:String = Json.toJson(InputSentenceForParser(premiseEnglish, claimEnglish)).toString()

    val numOfKnowledgeJapanese = premiseJapanese.size + claimJapanese.size
    val numOfKnowledgeEnglish = premiseEnglish.size + claimEnglish.size

    val deductionJapaneseList:List[AnalyzedSentenceObject] = numOfKnowledgeJapanese match{
      case 0 =>
        List.empty[AnalyzedSentenceObject]
      case _ =>
        val parseResultJapanese:String = ToposoidUtils.callComponent(japaneseInputSentences ,conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_JP_WEB_PORT"), "analyze", transversalState)
        val asos = Json.parse(parseResultJapanese).as[AnalyzedSentenceObjects].analyzedSentenceObjects
        //Add Informations of Images
        addImageInformation(asos, premiseJapanese:::claimJapanese, transversalState)
    }

    val deductionEnglishList:List[AnalyzedSentenceObject] = numOfKnowledgeEnglish match{
      case 0 =>
        List.empty[AnalyzedSentenceObject]
      case _ =>
        val parseResultEnglish:String = ToposoidUtils.callComponent(englishInputSentences ,conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_HOST"), conf.getString("TOPOSOID_SENTENCE_PARSER_EN_WEB_PORT"), "analyze", transversalState)
        val asos = Json.parse(parseResultEnglish).as[AnalyzedSentenceObjects].analyzedSentenceObjects
        //Add Informations of Images
        addImageInformation(asos, premiseEnglish:::claimEnglish, transversalState)
    }

    return deductionJapaneseList ::: deductionEnglishList

  }match {
    case Success(s) => s
    case Failure(e) => throw e
  }

  /**
   * This function recursively analyzes logical expressions in a tree structure and sentences.
   * @param t
   * @param result
   * @return
   */
  def analyzeRecursive(t : KnowledgeTree, result:ParsedKnowledgeTree, transversalState:TransversalState):ParsedKnowledgeTree = {
    t match {
      case KnowledgeNode(v, left, right) =>
        //Here, set the value to always be entered in left sisde.
        val r1 = analyzeRecursive(left, result, transversalState)
        if(v != "AND" && v != "OR" && r1.formula != "") return ParsedKnowledgeTree("-1",  r1.leafId, r1.subFormulaMap, r1.analyzedSentenceObjectsMap, r1.sentenceInfoMap, r1.sentenceMapForSat, r1.relations)
        val r2 = analyzeRecursive(right, r1, transversalState)
        val newFormula = r1.leafId match {
          case "-1" => "%s %s %s".format(r2.formula, r2.leafId, v)
          case _ => "%s %s %s %s".format(r2.formula, r1.leafId, r2.leafId, v)
        }
        val relations = List((List.empty[String], List.empty[PropositionRelation], List.empty[String], List.empty[PropositionRelation], List(r1.leafId, r2.leafId), List(PropositionRelation(v, 0, 1)) ))
        ParsedKnowledgeTree(r1.leafId,  newFormula, r2.subFormulaMap, r2.analyzedSentenceObjectsMap, r2.sentenceInfoMap, r2.sentenceMapForSat, r2.relations ++ relations)
      case KnowledgeLeaf(v) =>
        analyzeRecursiveSub(v, result, transversalState)
    }
  }
  /**
   *
   * @param v
   * @param result
   * @return
   */
  private def analyzeRecursiveSub(v:KnowledgeSentenceSet, result:ParsedKnowledgeTree, transversalState:TransversalState): ParsedKnowledgeTree ={

    if(v.claimList.size == 0 && v.premiseList.size == 0) return result

    val parseResult = this.parseKnowledgeSentence(v, transversalState)
    val sentenceMapForSat = (v.premiseList ++ v.claimList).foldLeft(result.sentenceMapForSat) {
      (acc, x) =>{
        if(!acc.isDefinedAt(x.sentence)){
          val newId:Int = acc.size match {
            case 0 => 1
            case _ => acc.values.max + 1
          }
          acc ++ Map(x.sentence -> newId)
        }else {
          acc
        }
      }
    }

    //Extract all the positionIds contained in the leaf while keeping the order.
    val premisePropositionIds:List[String] = parseResult.filter(_.knowledgeBaseSemiGlobalNode.sentenceType == PREMISE.index).map(_.nodeMap.head._2.propositionId).distinct
    val claimPropositionIds:List[String] = parseResult.filter(_.knowledgeBaseSemiGlobalNode.sentenceType == CLAIM.index).map(_.nodeMap.head._2.propositionId).distinct

    val premiseKnowledgeMap:Map[String, Knowledge] = (premisePropositionIds zip v.premiseList).groupBy(_._1).mapValues(_.map(_._2).head).toMap
    val claimKnowledgeMap:Map[String, Knowledge] = (claimPropositionIds zip v.claimList).groupBy(_._1).mapValues(_.map(_._2).head).toMap

    val sentenceInfoMap:Map[String, SentenceInfo] = (premiseKnowledgeMap ++ claimKnowledgeMap).foldLeft(Map.empty[String, SentenceInfo]){
      (acc, x) =>acc ++ Map(x._1 -> SentenceInfo(x._2.sentence, x._2.isNegativeSentence,  sentenceMapForSat.get(x._2.sentence).get.toString))
    }

    val leafId:String = claimPropositionIds.head

    //This process assigns an ID smaller than the premise to claim. The reason is that some propositions do not have a premise.
    val claimIds:List[String] = claimPropositionIds.map(z => {
      val sentence = sentenceInfoMap.get(z).get.sentence
      sentenceMapForSat.get(sentence).get.toString
    })

    val premiseIds:List[String] = premisePropositionIds.map(z => {
      val sentence = sentenceInfoMap.get(z).get.sentence
      sentenceMapForSat.get(sentence).get.toString
    })
    val premiseFormula:String = makeFormula(premiseIds, v.premiseLogicRelation, v.premiseList)
    val claimFormula:String = makeFormula(claimIds, v.claimLogicRelation, v.claimList)
    val subFormula = premiseFormula match {
      case "" => claimFormula
      case _ => "%s %s IMP".format(premiseFormula, claimFormula)
    }
    val subFormulaMap = result.subFormulaMap ++ Map(leafId.toString -> subFormula)
    val relations = result.relations ++ List((premisePropositionIds, v.premiseLogicRelation, claimPropositionIds, v.claimLogicRelation, List.empty[String], List.empty[PropositionRelation]))
    ParsedKnowledgeTree(leafId,  result.formula, subFormulaMap, result.analyzedSentenceObjectsMap ++ Map(leafId -> AnalyzedSentenceObjects(parseResult)), result.sentenceInfoMap ++ sentenceInfoMap, sentenceMapForSat, relations)
  }

  /**
   *　Configure logical expressions contained in KnowledgeSentenceSet
   * @param atoms
   * @param relations
   * @return
   */
  private def makeFormula(atoms:List[String], relations:List[PropositionRelation], sentenceInfoList:List[Knowledge]): String ={
    //extract NegativeSentence
    val negativeInfo:List[Boolean] = sentenceInfoList.map(_.isNegativeSentence)
    if(atoms.size == 0 && relations.size == 0) return ""
    if(atoms.size == 1 && relations.size == 0) {
      return negativeInfo.head match{
        case true => atoms.head + " NOT"
        case _ => atoms.head
      }
    }
    val formulas:List[String] = relations.map(x => {
      val sourceNot:String = negativeInfo(x.sourceIndex) match {
        case true => " NOT"
        case _ => ""
      }
      val destinationNot:String = negativeInfo(x.destinationIndex) match {
        case true => " NOT"
        case _ => ""
      }
      atoms(x.sourceIndex) + sourceNot + " " + atoms(x.destinationIndex)  + destinationNot + " " + x.operator
    })
    if(formulas.size == 1) return formulas.head
    formulas.drop(1).foldLeft(formulas.head){
      (acc, x) => acc + " " + x + " AND"
    }.trim
  }

}
