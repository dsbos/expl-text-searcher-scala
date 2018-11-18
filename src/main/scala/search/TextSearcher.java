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



public class TextSearcher {

    private final TextSearcherCore searcherImpl;

    public TextSearcher(File f) throws IOException {
        FileReader r = new FileReader(f);
        StringWriter w = new StringWriter();
        char[] buf = new char[4096];
        int readCount;

        while ((readCount = r.read(buf)) > 0) {
            w.write(buf,0,readCount);
        }

        searcherImpl = new TextSearcherCore(w.toString());
    }

    public String[] search(String queryWord, int contextWords) {
        return searcherImpl.search(queryWord, contextWords);
    }
}

