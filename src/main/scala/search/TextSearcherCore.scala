package search


class TextSearcherCore(indexedText: String) {

  /**
    * Represents an occurrence of a word in the indexed input test.
    * @param  occIndex      ordinal position among all word occurrences (for
    *                         indexing into sibling occurrences to get context
    *                         character range without re-scanning)
    * @param  startChOffset  occurrence's starting character offset in input
    * @param  endChOffset    occurrence's end character offset in inpur
    */
  private case class Occurrence(occIndex: Int,
                                startChOffset: Int,
                                endChOffset: Int)

  //??? maybe extract method, then declare "... wordOccurrences = m(...)"

  private val wordOccurrences = {

    sealed trait CharClass
    case object WordChar  extends CharClass
    case object OtherChar extends CharClass

    def charClassOf(ch: Char): CharClass = {
      val isWordChar =
      ('0' <= ch && ch <= '9') ||
      ('A' <= ch && ch <= 'Z') ||
      ('a' <= ch && ch <= 'z') ||
      ''' == ch
      if (isWordChar) WordChar else OtherChar
    }

    var occurrenceCount = 0
    var lastWordStartOffset = -1

    def buildOccurrence(wordEndOffset: Int): Occurrence = {
      val occurrenceOffset = occurrenceCount
      occurrenceCount += 1
      Occurrence(occurrenceOffset, lastWordStartOffset, wordEndOffset)
    }

    sealed trait TokenizerState
    case object InWord  extends TokenizerState
    case object InOther extends TokenizerState

    var inWord: TokenizerState = InOther
    (indexedText + ' ')   //????? try to fix end hack (avoid copying input)
        .zipWithIndex.flatMap({ case (ch, offset) =>

      (inWord, charClassOf(ch)) match {
        case (InOther, OtherChar) =>  // another non-word char
          None
        case (InOther, WordChar) =>   // first character of word--word start
          inWord = InWord
          lastWordStartOffset = offset
          None
        case (InWord,  WordChar) =>   // another word char
          None
        case (InWord,  OtherChar) =>  // non-word char after word--word end
          inWord = InOther
          Some(buildOccurrence(offset))
      }


    })
  }

  private def canonicalizeWord(word: String): String = word.toLowerCase

  //?? Maybe optimize:  index each occurrence as we find occurrences, when
  //   characters we just scanned, and Occurrences we created,  are still in cache

  private val occurrenceListsByWord: collection.Map[String, Seq[Occurrence]] = {
    val index = collection.mutable.Map[String, Seq[Occurrence]]()
    wordOccurrences.foreach(occurrence => {
      val occurrenceString = indexedText.substring(occurrence.startChOffset,
                                                 occurrence.endChOffset)
      // ?? is there any way to get the key for lookup without creating a
      //  new String object every time? (indexing 1000 occurrences of 10
      //  canonical words creates 1000 key strings, keeping 10; probably
      //  would need major customization/replacement of Map)
      val canonicalString = canonicalizeWord(occurrenceString)

      val currentSeq = index.getOrElseUpdate(canonicalString, Vector())
      val newSeq = currentSeq :+ occurrence
      index.put(canonicalString, newSeq)
    })
    index
  }

  def search(queryWord: String, contextWords: Int): Array[String] = {
    val canonicalQueryWord = canonicalizeWord(queryWord)
    val queryWordOccurrences: Seq[Occurrence] =
      occurrenceListsByWord.getOrElse(canonicalQueryWord, Nil)

    val hitsInContext = queryWordOccurrences.map(occurrence => {
      val hitOccIndex: Int = occurrence.occIndex

      // Compute context start--starting character offset of word occurrence
      //   contextWords before, if any; otherwise, first character offset of
      //   input text.
      val contextStartCharOffset = {
        val startOccurrenceIndex = hitOccIndex - contextWords
        if (wordOccurrences.isDefinedAt(startOccurrenceIndex)) {
          wordOccurrences(startOccurrenceIndex).startChOffset
        }
        else {
          indexedText.indices.start
        }
      }
      // Compute context end--similar to start, except re later occurrence and
      //   end of input.
      val contextEndCharOffset = {
        val endOccurrenceIndex = hitOccIndex + contextWords
        if (wordOccurrences.isDefinedAt(endOccurrenceIndex)) {
          wordOccurrences(endOccurrenceIndex).endChOffset
        }
        else {
          indexedText.indices.end
        }
      }

      val hitInContext = indexedText.substring(contextStartCharOffset,
                                               contextEndCharOffset)
      hitInContext
    })
    hitsInContext.toArray
  }

}

