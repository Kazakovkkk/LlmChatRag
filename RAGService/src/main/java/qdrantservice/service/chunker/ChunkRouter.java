package qdrantservice.service.chunker;

import qdrantservice.dto.ChunkingResult;
import qdrantservice.service.PdfTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkRouter {

    @Value("${chunking.strategy:semantic}")
    private String strategy;

    private final SemanticChunker semanticChunker;
    private final FixedSizeChunker fixedSizeChunker;
    private final RecursiveChunker recursiveChunker;
    private final PdfTextExtractor pdfTextExtractor;

    public ChunkingResult processText(String cleanText) {
        log.info("Выбрана стратегия чанкования: {}", strategy.toUpperCase());

        return switch (strategy.toLowerCase()) {
            case "fixed" -> {
                List<String> chunks = fixedSizeChunker.chunk(cleanText);
                yield new ChunkingResult(chunks, List.of(), List.of());
            }
            case "recursive" -> {
                List<String> chunks = recursiveChunker.chunk(cleanText);
                yield new ChunkingResult(chunks, List.of(), List.of());
            }
            case "semantic" -> {
                List<String> sentences = pdfTextExtractor.splitIntoSentences(cleanText);
                yield semanticChunker.chunkWithDetails(sentences);
            }
            default -> {
                log.warn("Неизвестная стратегия '{}', fallback на 'recursive'", strategy);
                List<String> chunks = recursiveChunker.chunk(cleanText);
                yield new ChunkingResult(chunks, List.of(), List.of());
            }
        };
    }
}