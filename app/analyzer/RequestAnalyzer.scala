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
import com.ideal.linked.toposoid.common.{CLAIM, PREMISE, ToposoidUtils}
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, DeductionResult}
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentence, KnowledgeLeaf, KnowledgeNode, KnowledgeTree}
import controllers.{ParsedKnowledgeTree, SentenceInfo}
import play.api.libs.json.Json

import scala.collection.immutable.ListMap
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
  def assignTrivialProposition(analyzedSentenceObjects:AnalyzedSentenceObjects, sentenceMapForSat:Map[String, SentenceInfo], subFormulaMap:Map[String, String]): (Map[String, String], Map[String, Option[DeductionResult]])  ={
    //This is a list of PropositionIds that can be found to be true or false as a result of searching GraphDB.
    val trivialPropositionIds:Map[String,Option[DeductionResult]] =
      analyzedSentenceObjects.analyzedSentenceObjects.foldLeft(Map.empty[String, Option[DeductionResult]]){
        (acc, x) =>
          acc ++ extractDeductionResult(x)
      }
    //This is converting the positionId to satId
    val trivialSatIds = trivialPropositionIds.foldLeft(Map.empty[String, Boolean]){
      (acc, x) => x._2 match {
        case Some(_) => acc ++ Map(sentenceMapForSat.get(x._1).get.satId -> x._2.get.status)
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
    val deductionResult:DeductionResult = analyzedSentenceObject.deductionResultMap.get(analyzedSentenceObject.sentenceType.toString).get
    val status:Option[DeductionResult] = deductionResult.matchedPropositionIds.size match {
      case 0 => None
      case _ => Some(deductionResult)
    }
    Map(propositionId -> status)
  }

  /**
   * This function delegates the processing of a given sentence to passer for each multilingual.
   * @param knowledgeSentenceSet
   * @return
   */
  def parseKnowledgeSentence(knowledgeSentenceSet: KnowledgeSentenceSet):List[AnalyzedSentenceObject] = Try{

    val premiseJapanese:List[Knowledge] = knowledgeSentenceSet.premiseList.filter(_.lang == "ja_JP")
    val claimJapanese:List[Knowledge] = knowledgeSentenceSet.claimList.filter(_.lang == "ja_JP")
    val premiseEnglish:List[Knowledge] = knowledgeSentenceSet.premiseList.filter(_.lang.startsWith("en_"))
    val claimEnglish:List[Knowledge] = knowledgeSentenceSet.claimList.filter(_.lang.startsWith("en_"))
    val japaneseInputSentences:String = Json.toJson(InputSentence(premiseJapanese, claimJapanese)).toString()
    val englishInputSentences:String = Json.toJson(InputSentence(premiseEnglish, claimEnglish)).toString()

    val numOfKnowledgeJapanese = premiseJapanese.size + claimJapanese.size
    val numOfKnowledgeEnglish = premiseEnglish.size + claimEnglish.size

    val deductionJapaneseList:List[AnalyzedSentenceObject] = numOfKnowledgeJapanese match{
      case 0 =>
        List.empty[AnalyzedSentenceObject]
      case _ =>
        val parseResultJapanese:String = ToposoidUtils.callComponent(japaneseInputSentences ,conf.getString("SENTENCE_PARSER_JP_WEB_HOST"), "9001", "analyze")
        Json.parse(parseResultJapanese).as[AnalyzedSentenceObjects].analyzedSentenceObjects
    }

    val deductionEnglishList:List[AnalyzedSentenceObject] = numOfKnowledgeEnglish match{
      case 0 =>
        List.empty[AnalyzedSentenceObject]
      case _ =>
        val parseResultEnglish:String = ToposoidUtils.callComponent(englishInputSentences ,conf.getString("SENTENCE_PARSER_EN_WEB_HOST"), "9007", "analyze")
        Json.parse(parseResultEnglish).as[AnalyzedSentenceObjects].analyzedSentenceObjects
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
  def analyzeRecursive(t : KnowledgeTree, result:ParsedKnowledgeTree):ParsedKnowledgeTree = {
    t match {
      case KnowledgeNode(v, left, right) =>
        val r1 = analyzeRecursive(left, result)
        if(v != "AND" && v != "OR") return ParsedKnowledgeTree("-1",  r1.leafId, r1.subFormulaMap, r1.analyzedSentenceObjects, r1.sentenceInfoMap, r1.sentenceMapForSat, r1.relations)
        val r2 = analyzeRecursive(right, r1)
        val newFormula = r1.leafId match {
          case "-1" => "%s %s %s".format(r2.formula, r2.leafId, v)
          case _ => "%s %s %s %s".format(r2.formula, r1.leafId, r2.leafId, v)
        }
        val relations = List((List.empty[String], List.empty[PropositionRelation], List.empty[String], List.empty[PropositionRelation], List(r1.leafId, r2.leafId), List(PropositionRelation(v, 0, 1)) ))
        ParsedKnowledgeTree(r1.leafId,  newFormula, r2.subFormulaMap, r2.analyzedSentenceObjects, r2.sentenceInfoMap, r2.sentenceMapForSat, r2.relations ++ relations)
      case KnowledgeLeaf(v) =>
        analyzeRecursiveSub(v, result)
    }
  }
  /**
   *
   * @param v
   * @param result
   * @return
   */
  private def analyzeRecursiveSub(v:KnowledgeSentenceSet, result:ParsedKnowledgeTree): ParsedKnowledgeTree ={
    val parseResult = this.parseKnowledgeSentence(v)
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
    val premisePropositionIds:List[String] = parseResult.filter(_.sentenceType == PREMISE.index).map(_.nodeMap.head._2.propositionId).distinct
    val claimPropositionIds:List[String] = parseResult.filter(_.sentenceType == CLAIM.index).map(_.nodeMap.head._2.propositionId).distinct

    val premiseKnowledgeMap:Map[String, Knowledge] = (premisePropositionIds zip v.premiseList).groupBy(_._1).mapValues(_.map(_._2).head)
    val claimKnowledgeMap:Map[String, Knowledge] = (claimPropositionIds zip v.claimList).groupBy(_._1).mapValues(_.map(_._2).head)

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
    ParsedKnowledgeTree(leafId,  result.formula, subFormulaMap, result.analyzedSentenceObjects:::parseResult, result.sentenceInfoMap ++ sentenceInfoMap, sentenceMapForSat, relations)
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
