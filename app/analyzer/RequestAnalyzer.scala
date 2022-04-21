package analyzer

import com.ideal.linked.common.DeploymentConverter.conf
import com.ideal.linked.toposoid.common.{CLAIM, PREMISE, ToposoidUtils}
import com.ideal.linked.toposoid.knowledgebase.model.KnowledgeBaseNode
import com.ideal.linked.toposoid.knowledgebase.regist.model.{Knowledge, KnowledgeSentenceSet, PropositionRelation}
import com.ideal.linked.toposoid.protocol.model.base.{AnalyzedSentenceObject, AnalyzedSentenceObjects, DeductionResult}
import com.ideal.linked.toposoid.protocol.model.parser.{InputSentence, KnowledgeLeaf, KnowledgeNode, KnowledgeTree}
import controllers.ParsedKnowledgeTree
import play.api.libs.json.Json

import scala.collection.immutable.ListMap
import scala.util.{Failure, Success, Try}
import io.jvm.uuid.UUID

class RequestAnalyzer {


  /**
   * This function performs the assignment of propositions whose truth is obvious from the knowledge graph
   * @param analyzedSentenceObjects
   * @param satIdMap
   * @param subFormulaMap
   * @return
   */
  def assignTrivialProposition(analyzedSentenceObjects:AnalyzedSentenceObjects, satIdMap:Map[String, String], subFormulaMap:Map[String, String]): (Map[String, String], Map[String, Option[DeductionResult]])  ={
    val reverseSatIdMap =  (satIdMap.values zip satIdMap.keys).groupBy(_._1).mapValues(_.map(_._2).head)
    //This is a list of PropositionIds that can be found to be true or false as a result of searching GraphDB.
    val trivialPropositionIds:Map[String,Option[DeductionResult]] =
      analyzedSentenceObjects.analyzedSentenceObjects.foldLeft(Map.empty[String, Option[DeductionResult]]){
        (acc, x) =>
          acc ++ extractDeductionResult(x)
      }
    //This is converting the positionId to satId
    val trivialSatIds = trivialPropositionIds.foldLeft(Map.empty[String, Boolean]){
      (acc, x) => x._2 match {
        case Some(_) => acc ++ Map(reverseSatIdMap.get(x._1).get -> x._2.get.status)
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
        if(v != "AND" && v != "OR") return ParsedKnowledgeTree("-1",  r1.satIdMap, r1.leafId, r1.subFormulaMap, r1.analyzedSentenceObjects, r1.sentenceMap, r1.sentenceMapForSat, r1.relations)
        val r2 = analyzeRecursive(right, r1)
        val newFormula = r1.leafId match {
          case "-1" => "%s %s %s".format(r2.formula, r2.leafId, v)
          case _ => "%s %s %s %s".format(r2.formula, r1.leafId, r2.leafId, v)
        }
        val relations = r1.leafId match {
          case "-1" => List((List.empty[String], List.empty[PropositionRelation], List.empty[String], List.empty[PropositionRelation], List("1", r2.leafId.toString), List(PropositionRelation(v, 0, 1))))
          case _ => List((List.empty[String], List.empty[PropositionRelation], List.empty[String], List.empty[PropositionRelation], List(r1.leafId.toString, r2.leafId.toString), List(PropositionRelation(v, 0, 1)) ))
        }
        ParsedKnowledgeTree("-1",  r2.satIdMap, newFormula, r2.subFormulaMap, r2.analyzedSentenceObjects, r2.sentenceMap, r2.sentenceMapForSat, r2.relations ++ relations)
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
    val sentenceMap = parseResult.foldLeft(Map.empty[String, String]){
      (acc, x) => acc ++ Map(x.nodeMap.head._2.propositionId -> getSentence(x.nodeMap))
    }
    val sentenceMapForSat = parseResult.foldLeft(result.sentenceMapForSat) {
      (acc, x) =>{
        if(!acc.isDefinedAt(getSentence(x.nodeMap))){
          val newId:Int = acc.size match {
            case 0 => 1
            case _ => acc.values.max + 1
          }
          acc ++ Map(getSentence(x.nodeMap) -> newId)
        }else {
          acc
        }
      }
    }
    //Extract all the positionIds contained in the leaf while keeping the order.
    val premisePropositionIds:List[String] = parseResult.filter(_.sentenceType == PREMISE.index).map(_.nodeMap.head._2.propositionId).distinct
    val claimPropositionIds:List[String] = parseResult.filter(_.sentenceType == CLAIM.index).map(_.nodeMap.head._2.propositionId).distinct

    val leafId:String = UUID.random.toString
    /*
    val leafId:Int = result.sentenceMapForSat.size match {
      case 0 => 1
      case _ => result.sentenceMapForSat.values.max + 1
    }
    */
    //This process assigns an ID smaller than the premise to claim. The reason is that some propositions do not have a premise.
    val claimIds:List[String] = claimPropositionIds.map(z => {
      val sentence = sentenceMap.get(z).get
      sentenceMapForSat.get(sentence).get.toString
    })

    val premiseIds:List[String] = premisePropositionIds.map(z => {
      val sentence = sentenceMap.get(z).get
      sentenceMapForSat.get(sentence).get.toString
    })
    val newPremiseSATidMap:Map[String, String] =  (premiseIds zip premisePropositionIds).groupBy(_._1).mapValues(_.map(_._2).head)
    val newClaimSATidMap:Map[String, String] =  (claimIds zip claimPropositionIds).groupBy(_._1).mapValues(_.map(_._2).head)
    val newSatIdMap = newPremiseSATidMap ++ newClaimSATidMap ++ result.satIdMap

    val premiseFormula:String = makeFormula(premiseIds, v.premiseLogicRelation, v.premiseList)
    val claimFormula:String = makeFormula(claimIds, v.claimLogicRelation, v.claimList)
    val subFormula = premiseFormula match {
      case "" => claimFormula
      case _ => "%s %s IMP".format(premiseFormula, claimFormula)
    }
    val subFormulaMap = result.subFormulaMap ++ Map(leafId.toString -> subFormula)
    val relations = result.relations ++ List((premiseIds, v.premiseLogicRelation, claimIds, v.claimLogicRelation, List.empty[String], List.empty[PropositionRelation]))
    ParsedKnowledgeTree(leafId,  newSatIdMap, result.formula, subFormulaMap, result.analyzedSentenceObjects:::parseResult, result.sentenceMap ++ sentenceMap, sentenceMapForSat, relations)
  }


  private def getSentence(nodeMap:Map[String,KnowledgeBaseNode]): String ={
    nodeMap.head._2.lang match {
      case "ja_JP" => ListMap(nodeMap.toSeq.sortBy(_._2.currentId):_*).map(_._2.surface).mkString
      case _ => ListMap(nodeMap.toSeq.sortBy(_._2.currentId):_*).map(_._2.surface).mkString(" ")
    }
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
