package qdrantservice.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PdfTextExtractor {

    public String extractText(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String rawText = stripper.getText(document);
            return cleanTextAdvanced(rawText);
        }
    }

    private String cleanTextAdvanced(String text) {
        if (text == null) return "";


        String[] lines = text.split("\\n");//символ конца строки
        return Arrays.stream(lines)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.matches(".*\\.{4,}.*"))
                .collect(Collectors.joining(" "))
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    public List<String> splitIntoSentences(String text) {
        String regex = "(?<![А-ЯA-Z][а-яa-z])(?<!г-н)(?<!т\\.д)(?<!т\\.п)(?<=[.!?])\\s+(?=[А-ЯA-Z])";

        String[] parts = text.split(regex);

        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> s.length() >= 15)
                .collect(Collectors.toList());
    }
}