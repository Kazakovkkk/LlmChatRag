package LlmChatRag.service;

import LlmChatRag.dto.DocumentDto;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentRankingService {

    public List<DocumentDto> rankDocuments(List<DocumentDto> documents) {
        // Используем текст чанка как ключ для дедупликации
        Map<String, DocumentDto> uniqueDocs = documents.stream()
                .collect(Collectors.toMap(
                        d -> d.getText().trim(),
                        d -> d,
                        (d1, d2) -> d1.getScore() >= d2.getScore() ? d1 : d2
                ));

        return uniqueDocs.values().stream()
                .sorted(Comparator.comparingDouble(DocumentDto::getScore).reversed())
                .toList();
    }

    public List<DocumentDto> getTopK(List<DocumentDto> documents, int k) {
        return rankDocuments(documents).stream()
                .limit(k)
                .toList();
    }
}