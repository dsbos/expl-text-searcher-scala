package search.impl

/**
  * Represents an occurrence of a word in the indexed input test.
  * @param  occIndex      ordinal position among all word occurrences (for
  *                         indexing into sibling occurrences to get context
  *                         character range without re-scanning)
  * @param  startChOffset  occurrence's starting character offset in input
  * @param  endChOffset    occurrence's end character offset in inpur
  */
private[impl] case class Occurrence(occIndex: Int,
                                    startChOffset: Int,
                                    endChOffset: Int)

