package search;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class TextSearcherSaved {

    /**
      * Represents an occurrence of a word in the indexed input text.
      */
    private static class Occurrence {
        private final int occIndex;
        private final int startChOffset;
        private final int endChOffset;

        /**
         * ...
         * @param  occIndex      ordinal position among all word occurrences (for
         *                          indexing into sibling occurrences to get
         *                          context character range without re-scanning)
         * @param  startChOffset  occurrence's starting character offset in input
         * @param  endChOffset    occurrence's ending character offset in input
         */
        public Occurrence(int occIndex, int startChOffset, int endChOffset) {
            this.occIndex = occIndex;
            this.startChOffset = startChOffset;
            this.endChOffset = endChOffset;
        }
        public int getOccIndex()      { return occIndex; }
        public int getStartChOffset() { return startChOffset; }
        public int getEndChOffset()   { return endChOffset; }
    }

    private final String indexedText;

    // Note:  Possible optimization:  Instead of storing (ArrayList) list of N
    //   occurrence objects, each with index and starting and ending character
    //   offset int values, store two int arrays, one of starting character
    //   offsets and one of ending character offsets.
    //   - Saves space:  object overhead of N objects, plus N pointers.
    //   - Might help speed re CPU cache (data for logically adjacent
    //     occurrences is adjacent array elements, in nearby memory, vs. in
    //     other Occurrence objects, likely in farther-away memory in the heap).
    // Note:  Less likely possible space-over-speed optimization:  Store only
    //   starting character offset, using re-parsing to find ending character
    //   offset.  (Change word index from listing Occurrence objects (or indexes
    //   into parallel start-and-end-offset arrays) to directly listing
    //   starting offsets.)
    //   - Eliminates space for occurrence objects list or offset arrays.
    //   - Costs time to re-parse original text from starting character offset
    //     of hit backwards to beginning of context and forwards to end of
    //     context.


    private final List<Occurrence> wordOccurrences;

    private final Map<String, List<Occurrence>> occurrenceListsByWord;


    /**
     * Reports whether given character counts as part of a word.
     */
    private static boolean isWordChar(final char ch) {
        return (    '0' <= ch && ch <= '9')
                || ('A' <= ch && ch <= 'Z')
                || ('a' <= ch && ch <= 'z')
                || '\'' == ch;
    }

    private static String canonicalizeWord(final String occurrenceForm) {
        return occurrenceForm.toLowerCase();
    }

    /** Lexing (tokenizing) state. */
    private static enum LexState { InWord, InOther }

    private static void addOccurrence(final List<Occurrence> occurrences,
                                      final int startChOffset,
                                      final int endCharOffset) {
        // What new occurrence's offset in ordered collection will be once added:
        final int occOffset = occurrences.size();
        occurrences.add(new Occurrence(occOffset, startChOffset, endCharOffset));
    }

    /**
     * Parses input text into list of locations of word occurrences in input
     * text.
     */
    private static List<Occurrence> findWordOccurrences(final String inputText) {
        final List<Occurrence> occurrences = new ArrayList<>();

        int lastWordStartOffset = -1;  // (Initial value should be unused.)

        // Handle beginning of text:
        LexState lexState = LexState.InOther;  // Start not in any word.

        // Handle text:
        for (int cx = 0; cx < inputText.length(); cx++) {
            final char ch = inputText.charAt(cx);
            final boolean isWordChar = isWordChar(ch);
            switch (lexState) {
                case InOther:
                    if (! isWordChar) {
                        // Not in word; a(nother) non-word char.--no-op.
                    }
                    else {
                        // Not in word; a word character--word start.
                        lexState = LexState.InWord;
                        lastWordStartOffset = cx;
                    }
                    break;
                case InWord:
                    if (isWordChar) {
                        // In word; another word char.--no-op.
                    }
                    else {
                        // In word; a non-word char.--word end.
                        lexState = LexState.InOther;
                        addOccurrence(occurrences, lastWordStartOffset, cx);
                    }
                    break;
            }
        }
        // Handle end of text:
        switch (lexState) {
            case InOther:
                // Ended not in word--nothing to wrap up/
                break;
            case InWord:
                // Ended in word (not yet terminated)--word end.
                lexState = LexState.InOther;  // (not really needed)
              addOccurrence(occurrences,
                            lastWordStartOffset, inputText.length());
              break;
        }
        return Collections.unmodifiableList(occurrences);
    }

    // Note:  Possible optimization:  Index each occurrence when we find each
    //   occurrence, when the characters we just scanned, and the Occurrence we
    //   just created, are still in CPU cache, rather than doing the indexing
    //   in a second pass, when those characters and that Occurrence are more
    //   likely to have been evicted from the cache.

    /**
     * Creates index of given word occurrences in given corresponding text.
     * @param  inputText        base text corresponding to given occurrence
     *                            locations
     * @param  wordOccurrences  given occurrence locations
     * @return
     */
    private static Map<String, List<Occurrence>> indexOccurrences(
            String inputText,
            List<Occurrence> wordOccurrences) {

        final Map<String, List<Occurrence>> occurrencesByWord = new HashMap<>();

        wordOccurrences.forEach(occurrence -> {
            String occurrenceForm =
                    inputText.substring(occurrence.getStartChOffset(),
                                        occurrence.getEndChOffset());
            String canonicalForm = canonicalizeWord(occurrenceForm);
            List<Occurrence> currentList =
                    occurrencesByWord.computeIfAbsent(canonicalForm,
                                                      word -> new ArrayList<>());
            currentList.add(occurrence);
        });
        return Collections.unmodifiableMap(occurrencesByWord);
    }


    /**
     * Initializes the text searcher with the contents of a text file.
     * The current implementation just reads the contents into a string
     * and passes them to #init().  You may modify this implementation if you need to.
     *
     * @param f Input file.
     * @throws IOException
     */
    public TextSearcherSaved(File f) throws IOException {
        FileReader r = new FileReader(f);
        StringWriter w = new StringWriter();
        char[] buf = new char[4096];
        int readCount;

        while ((readCount = r.read(buf)) > 0) {
            w.write(buf,0,readCount);
        }

        // Note:  If original API (e.g., with init(...)) method were required,
        // these three fields and initializations could be moved to a
        // subcomponent class:

        indexedText = w.toString();
        wordOccurrences = findWordOccurrences(indexedText);
        occurrenceListsByWord = indexOccurrences(indexedText, wordOccurrences);
        // (Static methods and explicit passing are to try to be reduce chance
        // of errors (originally and in hypothetical future refactoring).)
    }

    // Note:  Possible optimization:  If client can accept CharSequence[]
    //   instead of necessarily String[], return instances of an implementation
    //   of CharSequence that indexes into indexedText, re-using indexedText's
    //   already allocated character array, rather than making its own
    //   character array to hold a copy of the substring of characters it
    //   presents (as String.substring(...) methods do).)
    //   - Saves array object overhead and N characters for each hit result.
    //   - Caveat:  Holding onto any such hit-result CharSequence object blocks
    //     garbage-collecting indexedText's possibly huge character array.

    /**
     * ...
     * @param queryWord The word to search for in the file contents.
     * @param contextWords The number of words of context to provide on
     *                     each side of the query word.
     * @return One context string for each time the query word appears in the file.
     */
    public String[] search(String queryWord, int contextWords) {
        final String canonicalQueryWord = canonicalizeWord(queryWord);
        final List<Occurrence> queryWordOccurrences =
                occurrenceListsByWord.getOrDefault(canonicalQueryWord,
                                                   Collections.emptyList());
        // (Named values to try to make semantics of uses clearer.)
        final int minOccIndex = 0;
        final int maxOccIndex = wordOccurrences.size() - 1;  // (Indexed)
        final int minTextCharOffset = 0;
        final int maxTextCharOffset = indexedText.length();  // (Used as range)

        final List<String> hitsInContext =
            queryWordOccurrences.stream().map(occurrence -> {
                final int hitOccIndex = occurrence.getOccIndex();

                final int contextStartCharOffset;
                {
                    final int startOccurrenceIndex = hitOccIndex - contextWords;
                    if (startOccurrenceIndex < minOccIndex) {
                        contextStartCharOffset = minTextCharOffset;
                    }
                    else {
                        Occurrence startOcc =
                                wordOccurrences.get(startOccurrenceIndex);
                        contextStartCharOffset = startOcc.getStartChOffset();
                    }
                }
                final int contextEndCharOffset;
                {
                    final int endOccurrenceIndex = hitOccIndex + contextWords;
                    if (endOccurrenceIndex > maxOccIndex) {
                        contextEndCharOffset = maxTextCharOffset;
                    }
                    else {
                        Occurrence endOccurrence =
                                wordOccurrences.get(endOccurrenceIndex);
                        contextEndCharOffset = endOccurrence.getEndChOffset();
                    }
                }

                String hitInContext = indexedText.substring(contextStartCharOffset,
                                                            contextEndCharOffset);
                return hitInContext;

            }).collect(Collectors.toList());
        return hitsInContext.toArray(new String[0]);
    }
}

