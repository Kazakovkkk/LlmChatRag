package qdrantservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class BM25Tokenizer {

    private static final float K1 = 1.5f;
    private static final float B = 0.75f;

    private final BM25StatsRepository statsRepository;

    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "на", "с", "по", "для", "не", "это", "как", "что",
            "а", "но", "или", "из", "о", "от", "до", "за", "при", "к",
            "the", "a", "an", "in", "on", "at", "for", "to", "of", "is"
    );

    public void indexDocument(String statsCollection, String documentId, String text) {
        List<String> terms = tokenizeToTerms(text);
        int docLength = terms.size();

        // 1. Сохраняем длину текущего документа
        statsRepository.saveDocumentLength(statsCollection, documentId, docLength);

        // 2. Считываем текущую глобальную метрику или создаем новую
        var globalMetaOpt = statsRepository.loadGlobalStats(statsCollection);
        int oldTotalDocs = globalMetaOpt.map(BM25StatsRepository.GlobalStatsMeta::totalDocuments).orElse(0);
        double oldAvgLen = globalMetaOpt.map(BM25StatsRepository.GlobalStatsMeta::avgDocumentLength).orElse(0.0);

        int newTotalDocs = oldTotalDocs + 1;
        double newAvgLen = ((oldAvgLen * oldTotalDocs) + docLength) / newTotalDocs;

        // 3. Апдейтим глобальные параметры
        statsRepository.saveGlobalStats(statsCollection, newTotalDocs, newAvgLen);

        // 4. Покомпонентно инкрементируем частоту уникальных слов
        Set<String> uniqueTerms = new HashSet<>(terms);
        for (String term : uniqueTerms) {
            int currentFreq = statsRepository.loadTermFrequency(statsCollection, term);
            statsRepository.saveTermFrequency(statsCollection, term, currentFreq + 1);
        }
    }

    public void clearCollectionStats(String statsCollection) {
        log.warn("🗑️ Запрос на каскадное удаление метаданных BM25 из репозитория для коллекции: {}", statsCollection);
        try {
            statsRepository.deleteCollectionStats(statsCollection);
            log.info("✅ Статистические матрицы BM25 для коллекции [{}] успешно очищены в БД", statsCollection);
        } catch (Exception e) {
            log.error("❌ Не удалось очистить BM25StatsRepository для {}: {}", statsCollection, e.getMessage());
            // Не выбрасываем исключение жестко, чтобы не блокировать очистку самого Qdrant, если БД пуста
        }
    }

    public Map<Integer, Float> tokenizeWithBM25(String statsCollection, String text, String documentId, boolean isDocument) {
        List<String> terms = tokenizeToTerms(text);
        if (terms.isEmpty()) return Map.of();

        int docLength = isDocument ? terms.size() : statsRepository.loadDocumentLength(statsCollection, documentId);

        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : terms) {
            termFreq.merge(term, 1, Integer::sum);
        }

        Map<Integer, Float> sparseVector = new HashMap<>();
        var globalMeta = statsRepository.loadGlobalStats(statsCollection).orElse(new BM25StatsRepository.GlobalStatsMeta(1, 1.0));

        int N = Math.max(globalMeta.totalDocuments(), 1);
        double avgDL = Math.max(globalMeta.avgDocumentLength(), 1.0);

        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();

            int df = statsRepository.loadTermFrequency(statsCollection, term);
            double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1.0);

            float score;
            if (isDocument) {
                double tfNorm = (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * docLength / avgDL));
                score = (float) (idf * tfNorm);
            } else {
                score = (float) idf;
            }

            if (score > 0) {
                int index = Math.abs(term.hashCode()) % 100000;
                sparseVector.merge(index, score, Float::sum);
            }
        }
        return sparseVector;
    }

    public List<String> tokenizeToTerms(String text) {
        String[] words = text.toLowerCase()
                .replaceAll("[^a-zA-Zа-яА-Я0-9\\s]", " ")
                .split("\\s+");

        List<String> terms = new ArrayList<>();
        for (String word : words) {
            if (word.length() > 2 && !STOP_WORDS.contains(word)) {
                terms.add(stem(word));
            }
        }
        return terms;
    }

    private String stem(String word) {
        if (word.endsWith("ами") || word.endsWith("ями") || word.endsWith("ыми"))
            return word.substring(0, word.length() - 3);
        if (word.endsWith("ого") || word.endsWith("его"))
            return word.substring(0, word.length() - 3);
        if (word.endsWith("ому") || word.endsWith("ему"))
            return word.substring(0, word.length() - 3);
        if (word.endsWith("ах") || word.endsWith("ях"))
            return word.substring(0, word.length() - 2);
        if (word.endsWith("ов") || word.endsWith("ев"))
            return word.substring(0, word.length() - 2);
        if (word.endsWith("ий") || word.endsWith("ый") || word.endsWith("ая") || word.endsWith("ое"))
            return word.substring(0, word.length() - 2);
        if (word.endsWith("ть") || word.endsWith("ти"))
            return word.substring(0, word.length() - 2);
        if (word.endsWith("ет") || word.endsWith("ит") || word.endsWith("ат") || word.endsWith("ят"))
            return word.substring(0, word.length() - 2);
        if (word.endsWith("а") || word.endsWith("я") || word.endsWith("е") || word.endsWith("и") || word.endsWith("ы"))
            return word.length() > 4 ? word.substring(0, word.length() - 1) : word;
        if (word.endsWith("ing") && word.length() > 5)
            return word.substring(0, word.length() - 3);
        if (word.endsWith("tion") && word.length() > 6)
            return word.substring(0, word.length() - 4);
        if (word.endsWith("ed") && word.length() > 4)
            return word.substring(0, word.length() - 2);
        if (word.endsWith("es") && word.length() > 4)
            return word.substring(0, word.length() - 2);
        if (word.endsWith("s") && word.length() > 3)
            return word.substring(0, word.length() - 1);
        return word;
    }

}