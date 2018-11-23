package search.impl


class TextSearcherCore(indexedText: String) {


  //??? maybe extract method, then declare "... wordOccurrences = m(...)",
  // so occurrenceListsByWord can be declared close by (so they and indexedText
  // are all declared near the top of the class; maybe move methods to separate
  // class (nested? sibling?)

  private val wordOccurrences: Vector[Occurrence] = {

    // Note:  This block of code doesn't seem to be as clean as it could be.

    sealed trait CharClass
    case object WordChar  extends CharClass
    case object OtherChar extends CharClass

    def charClassOf(ch: Char): CharClass = {
      val isWordChar =
        '\'' == ch ||
        ('0' <= ch && ch <= '9') ||
        ('A' <= ch && ch <= 'Z') ||
        ('a' <= ch && ch <= 'z')
      if (isWordChar) WordChar else OtherChar
    }

    val occurrences = collection.mutable.ArrayBuffer[Occurrence]()

    def addOccurrence(startCharOffset: Int,
                      endCharOffset: Int): Unit = {
      val occurrenceOffset = occurrences.size
      occurrences += Occurrence(occurrenceOffset, startCharOffset, endCharOffset)
    }

    sealed trait LexState
    case object InWord  extends LexState
    case object InOther extends LexState

    var lexState: LexState = InOther
    var lastWordStartOffset = -1

    indexedText.zipWithIndex.foreach({ case (ch, offset) =>
      (lexState, charClassOf(ch)) match {
        case (InOther, OtherChar) =>  // another non-word char
        case (InOther, WordChar) =>   // first character of word--word start
          lexState = InWord
          lastWordStartOffset = offset  // remember start offset
        case (InWord,  WordChar) =>   // another word char
        case (InWord,  OtherChar) =>  // non-word char after word--word end
          lexState = InOther
          addOccurrence(lastWordStartOffset, offset)  // add word
      }
    })
    if (lexState == InWord) {
      addOccurrence(lastWordStartOffset, indexedText.indices.end)
    }
    occurrences.toVector
  }

  private def canonicalizeWord(word: String): String = word.toLowerCase

  //?? Maybe optimize:  index each occurrence as we find occurrences, when
  //   characters we just scanned, and Occurrences we created, are still in cache

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

