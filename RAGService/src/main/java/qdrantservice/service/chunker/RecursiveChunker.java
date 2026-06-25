package qdrantservice.service.chunker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RecursiveChunker {

    @Value("${chunking.recursive.max-size:1000}")
    private int maxSize;

    @Value("${chunking.recursive.overlap:200}")
    private int overlap;

    private static final List<String> DEFAULT_SEPARATORS = List.of("\n\n", "\n", ". ", " ", "");

    public List<String> chunk(String text) {
        log.info("Используется Recursive chunking (max-size: {}, overlap: {})", maxSize, overlap);
        return splitText(text, DEFAULT_SEPARATORS);
    }

    private List<String> splitText(String text, List<String> separators) {
        List<String> finalChunks = new ArrayList<>();
        if (text.length() <= maxSize) {
            finalChunks.add(text.trim());
            return finalChunks;
        }

        String separator = separators.get(separators.size() - 1);
        List<String> newSeparators = new ArrayList<>();

        for (int i = 0; i < separators.size(); i++) {
            String sep = separators.get(i);
            if (sep.isEmpty() || text.contains(sep)) {
                separator = sep;
                newSeparators = separators.subList(i + 1, separators.size());
                break;
            }
        }

        String[] splits = separator.isEmpty() ? text.split("") : text.split(Pattern.quote(separator));
        List<String> goodSplits = new ArrayList<>();

        for (String s : splits) {
            if (s.length() < maxSize) {
                goodSplits.add(s);
            } else {
                if (!newSeparators.isEmpty()) {
                    goodSplits.addAll(splitText(s, newSeparators));
                } else {
                    goodSplits.add(s); // Форсированное добавление, если разделителей не осталось
                }
            }
        }

        return mergeSplits(goodSplits, separator);
    }

    private List<String> mergeSplits(List<String> splits, String separator) {
        List<String> chunks = new ArrayList<>();
        StringBuilder currentDoc = new StringBuilder();
        int currentLength = 0;

        for (String split : splits) {
            int len = split.length() + (currentLength > 0 ? separator.length() : 0);

            if (currentLength + len > maxSize && currentLength > 0) {
                chunks.add(currentDoc.toString().trim());
                currentDoc = new StringBuilder(split);
                currentLength = split.length();
            } else {
                if (currentLength > 0) {
                    currentDoc.append(separator);
                }
                currentDoc.append(split);
                currentLength += len;
            }
        }

        if (currentLength > 0) {
            chunks.add(currentDoc.toString().trim());
        }

        return chunks;
    }
}