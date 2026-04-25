package qdrantservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class BM25Tokenizer {

    private static final float K1 = 1.5f;
    private static final float B = 0.75f;

    private final Map<String, Integer> documentFrequency = new ConcurrentHashMap<>();
    private final AtomicInteger totalDocuments = new AtomicInteger(0);
    private final Map<String, Integer> documentLengths = new ConcurrentHashMap<>();
    private volatile double avgDocumentLength = 0.0;
    private final AtomicLong totalLengthSum = new AtomicLong(0);

    private final BM25StatsRepository statsRepository;

    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "на", "с", "по", "для", "не", "это", "как", "что",
            "а", "но", "или", "из", "о", "от", "до", "за", "при", "к",
            "the", "a", "an", "in", "on", "at", "for", "to", "of", "is",
            "are", "was", "were", "be", "been", "has", "have", "had"
    );

    public BM25Tokenizer(BM25StatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    // ─── Загрузка статистики при старте ──────────────────────
    @PostConstruct
    public void loadStats() {
        log.info("Загрузка BM25 статистики из Qdrant...");

        // Загружаем глобальную статистику
        statsRepository.loadGlobalStats().ifPresentOrElse(
                stats -> {
                    totalDocuments.set(stats.totalDocuments());
                    avgDocumentLength = stats.avgDocumentLength();
                    documentLengths.putAll(stats.documentLengths());
                    log.info("BM25 глобальная статистика загружена | docs: {} | avgLen: {}",
                            stats.totalDocuments(), stats.avgDocumentLength());
                },
                () -> log.info("BM25 статистика не найдена — начинаем с нуля")
        );

        // Загружаем document frequency
        Map<String, Integer> df = statsRepository.loadDocumentFrequency();
        if (!df.isEmpty()) {
            documentFrequency.putAll(df);
            log.info("BM25 document frequency загружен | {} терминов", df.size());
        }

        logStats();
    }

    // ─── Индексация документа ─────────────────────────────────
    public void indexDocument(String documentId, String text) {
        List<String> terms = tokenizeToTerms(text);
        int docLength = terms.size();

        documentLengths.put(documentId, docLength);

        int totalDocs = totalDocuments.incrementAndGet();
        totalLengthSum.addAndGet(docLength);
        avgDocumentLength = (double) totalLengthSum.get() / totalDocs;

        Set<String> uniqueTerms = new HashSet<>(terms);
        for (String term : uniqueTerms) {
            documentFrequency.merge(term, 1, Integer::sum);
        }

        // Сохраняем в Qdrant асинхронно каждые 10 документов
        if (totalDocs % 10 == 0) {
            persistStats();
        }

        log.debug("Индексирован документ {} | {} терминов | avgDocLen: {}",
                documentId, docLength, avgDocumentLength);
    }

    // ─── Принудительное сохранение статистики ────────────────
    public void persistStats() {
        log.info("Сохраняем BM25 статистику в Qdrant...");
        statsRepository.saveGlobalStats(
                totalDocuments.get(),
                avgDocumentLength,
                new HashMap<>(documentLengths)
        );
        statsRepository.saveDocumentFrequency(new HashMap<>(documentFrequency));
        log.info("BM25 статистика сохранена");
    }

    // ─── Остальные методы без изменений ──────────────────────
    public Map<Integer, Float> tokenize(String text) {
        return tokenizeWithBM25(text, true);
    }

    public Map<Integer, Float> tokenizeQuery(String text) {
        return tokenizeWithBM25(text, false);
    }

    private Map<Integer, Float> tokenizeWithBM25(String text, boolean isDocument) {
        List<String> terms = tokenizeToTerms(text);
        if (terms.isEmpty()) return Map.of();

        int docLength = terms.size();

        Map<String, Integer> termFreq = new HashMap<>();
        for (String term : terms) {
            termFreq.merge(term, 1, Integer::sum);
        }

        Map<Integer, Float> sparseVector = new HashMap<>();
        int N = Math.max(totalDocuments.get(), 1);
        double avgDL = Math.max(avgDocumentLength, 1.0);

        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();

            int df = documentFrequency.getOrDefault(term, 0);
            double idf = Math.log((N - df + 0.5) / (df + 0.5) + 1.0);

            float score;
            if (isDocument) {
                double tfNorm = (tf * (K1 + 1))
                        / (tf + K1 * (1 - B + B * docLength / avgDL));
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
        if (word.endsWith("ами") || word.endsWith("ями"))
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

    public void logStats() {
        log.info("BM25 статистика | Документов: {} | Уникальных терминов: {} | avgDocLen: {}",
                totalDocuments.get(),
                documentFrequency.size(),
                String.format("%.1f", avgDocumentLength));
    }
    public int getTotalDocuments() { return totalDocuments.get(); }
    public double getAvgDocumentLength() { return avgDocumentLength; }
    public Map<String, Integer> getDocumentFrequency() { return documentFrequency; }
}
