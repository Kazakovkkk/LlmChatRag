package qdrantservice.service;

import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import io.qdrant.client.grpc.JsonWithInt.Value;

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

    // Полностью обновленный метод в BM25Tokenizer.java

    public void indexDocument(String statsCollection, String documentId, String text) {
        List<String> terms = tokenizeToTerms(text);
        int docLength = terms.size();
        Set<String> uniqueTerms = new HashSet<>(terms);

        if (uniqueTerms.isEmpty()) return;

        // 1. Читаем глобальную статистику (1-й сетевой запрос)
        var globalMetaOpt = statsRepository.loadGlobalStats(statsCollection);
        int oldTotalDocs = globalMetaOpt.map(BM25StatsRepository.GlobalStatsMeta::totalDocuments).orElse(0);
        double oldAvgLen = globalMetaOpt.map(BM25StatsRepository.GlobalStatsMeta::avgDocumentLength).orElse(0.0);

        int newTotalDocs = oldTotalDocs + 1;
        double newAvgLen = ((oldAvgLen * oldTotalDocs) + docLength) / newTotalDocs;

        // 2. Читаем частоты ВСЕХ уникальных слов чанка одной пачкой (2-й сетевой запрос)
        Map<String, Integer> currentFreqs = statsRepository.loadTermFrequenciesBatch(statsCollection, uniqueTerms);

        // Список для агрегации всех точек, которые нужно обновить/создать
        List<Points.PointStruct> pointsToUpsert = new ArrayList<>();

        // Собираем точку глобальной статистики
        Map<String, Value> globalPayload = new HashMap<>();
        globalPayload.put("total_documents", Value.newBuilder().setIntegerValue(newTotalDocs).build());
        globalPayload.put("avg_document_length", Value.newBuilder().setDoubleValue(newAvgLen).build());
        globalPayload.put("is_system_meta", Value.newBuilder().setBoolValue(true).build());
        pointsToUpsert.add(statsRepository.buildServicePoint("00000000-0000-0000-0000-000000000001", globalPayload));

        // Собираем точку длины текущего документа
        String docLenUuid = UUID.nameUUIDFromBytes(("doclen_" + documentId).getBytes()).toString();
        Map<String, Value> docLenPayload = new HashMap<>();
        docLenPayload.put("doc_id", Value.newBuilder().setStringValue(documentId).build());
        docLenPayload.put("length", Value.newBuilder().setIntegerValue(docLength).build());
        docLenPayload.put("is_doc_length", Value.newBuilder().setBoolValue(true).build());
        pointsToUpsert.add(statsRepository.buildServicePoint(docLenUuid, docLenPayload));

        // Накапливаем изменения для частоты каждого слова в памяти
        for (String term : uniqueTerms) {
            int currentFreq = currentFreqs.getOrDefault(term, 0);
            int newFreq = currentFreq + 1;

            String termUuid = UUID.nameUUIDFromBytes(term.getBytes()).toString();
            Map<String, Value> termPayload = new HashMap<>();
            termPayload.put("term", Value.newBuilder().setStringValue(term).build());
            termPayload.put("frequency", Value.newBuilder().setIntegerValue(newFreq).build());
            termPayload.put("is_term", Value.newBuilder().setBoolValue(true).build());

            pointsToUpsert.add(statsRepository.buildServicePoint(termUuid, termPayload));
        }

        // 3. Отправляем всю пачку данных в Qdrant ОДНИМ запросом (3-й сетевой запрос)
        statsRepository.upsertPointsBatch(statsCollection, pointsToUpsert);
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

        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : terms) {
            termFreq.merge(term, 1, Integer::sum);
        }

        // docLength нужен только для документа (BM25-нормализация tf), для запроса не используется
        int docLength = isDocument ? terms.size() : 0;

        // ОДИН пакетный запрос вместо N точечных
        Map<String, Integer> dfMap = statsRepository.loadTermFrequenciesBatch(statsCollection, termFreq.keySet());

        Map<Integer, Float> sparseVector = new HashMap<>();
        var globalMeta = statsRepository.loadGlobalStats(statsCollection)
                .orElse(new BM25StatsRepository.GlobalStatsMeta(1, 1.0));

        int N = Math.max(globalMeta.totalDocuments(), 1);
        double avgDL = Math.max(globalMeta.avgDocumentLength(), 1.0);

        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();

            int df = dfMap.getOrDefault(term, 0);
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