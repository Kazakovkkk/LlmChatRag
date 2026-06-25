package qdrantservice.service.chunker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class FixedSizeChunker {

    @Value("${chunking.fixed.size:1000}")
    private int chunkSize;

    @Value("${chunking.fixed.overlap:200}")
    private int chunkOverlap;

    public List<String> chunk(String text) {
        log.info("Используется Fixed-size chunking (size: {}, overlap: {})", chunkSize, chunkOverlap);
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int step = chunkSize - chunkOverlap;
        if (step <= 0) {
            log.warn("Overlap больше или равен размеру чанка. Сбрасываем overlap в 0.");
            step = chunkSize;
        }

        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end).trim());

            // Если достигли конца текста — прерываем цикл
            if (end == text.length()) {
                break;
            }
        }

        return chunks;
    }
}