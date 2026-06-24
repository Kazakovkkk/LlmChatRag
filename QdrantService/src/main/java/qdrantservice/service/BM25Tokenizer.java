package qdrantservice.service;

import io.qdrant.client.grpc.Points.PointStruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class BM25Tokenizer {

    private static final float K1 = 1.5f;
    private static final float B = 0.75f;

    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "на", "с", "по", "для", "не", "это",
            "как", "что", "а", "но", "или", "из", "о", "от",
            "до", "за", "при", "к",
            "the", "a", "an", "in", "on", "at", "for",
            "to", "of", "is"
    );

    private final BM25StatsRepository statsRepository;

    private final Map<String, Object> collectionLocks =
            new ConcurrentHashMap<>();

    public void upsertDocument(
            String statsCollection,
            String documentId,
            String text
    ) {
        synchronized (lockFor(statsCollection)) {
            List<String> tokens = tokenizeToTerms(text);
            Set<String> newTerms = new HashSet<>(tokens);
            int newLength = tokens.size();

            Optional<BM25StatsRepository.DocumentStats> oldStatsOptional =
                    statsRepository.loadDocumentStats(
                            statsCollection,
                            documentId
                    );

            Set<String> oldTerms = oldStatsOptional
                    .map(BM25StatsRepository.DocumentStats::terms)
                    .orElseGet(Set::of);

            int oldLength = oldStatsOptional
                    .map(BM25StatsRepository.DocumentStats::length)
                    .orElse(0);

            BM25StatsRepository.GlobalStatsMeta global =
                    statsRepository.loadGlobalStats(statsCollection)
                            .orElse(
                                    new BM25StatsRepository.GlobalStatsMeta(
                                            0,
                                            0.0
                                    )
                            );

            int oldDocumentCount = global.totalDocuments();

            double oldTotalLength =
                    oldDocumentCount
                            * global.avgDocumentLength();

            boolean documentAlreadyExists =
                    oldStatsOptional.isPresent();

            int newDocumentCount = documentAlreadyExists
                    ? oldDocumentCount
                    : oldDocumentCount + 1;

            double newTotalLength;

            if (documentAlreadyExists) {
                newTotalLength =
                        oldTotalLength
                                - oldLength
                                + newLength;
            } else {
                newTotalLength =
                        oldTotalLength
                                + newLength;
            }

            newTotalLength = Math.max(
                    0.0,
                    newTotalLength
            );

            double newAverageLength =
                    newDocumentCount == 0
                            ? 0.0
                            : newTotalLength
                            / newDocumentCount;

            Set<String> allChangedTerms =
                    new HashSet<>(oldTerms);

            allChangedTerms.addAll(newTerms);

            Map<String, Integer> currentFrequencies =
                    statsRepository.loadTermFrequenciesBatch(
                            statsCollection,
                            allChangedTerms
                    );

            List<PointStruct> pointsToUpsert =
                    new ArrayList<>();

            List<String> pointsToDelete =
                    new ArrayList<>();

            for (String term : allChangedTerms) {
                int delta =
                        (newTerms.contains(term) ? 1 : 0)
                                - (oldTerms.contains(term) ? 1 : 0);

                if (delta == 0) {
                    continue;
                }

                int currentFrequency =
                        currentFrequencies.getOrDefault(
                                term,
                                0
                        );

                int newFrequency = Math.max(
                        0,
                        currentFrequency + delta
                );

                if (newFrequency == 0) {
                    pointsToDelete.add(
                            statsRepository.getTermPointId(term)
                    );
                } else {
                    pointsToUpsert.add(
                            statsRepository
                                    .buildTermFrequencyPoint(
                                            term,
                                            newFrequency
                                    )
                    );
                }
            }

            pointsToUpsert.add(
                    statsRepository.buildGlobalStatsPoint(
                            newDocumentCount,
                            newAverageLength
                    )
            );

            pointsToUpsert.add(
                    statsRepository.buildDocumentStatsPoint(
                            documentId,
                            newLength,
                            newTerms
                    )
            );

            try {
                statsRepository.upsertPointsBatch(
                        statsCollection,
                        pointsToUpsert
                );

                statsRepository.invalidateGlobalStats(
                        statsCollection
                );

                statsRepository.deletePointsBatch(
                        statsCollection,
                        pointsToDelete
                );

                statsRepository.cacheGlobalStats(
                        statsCollection,
                        new BM25StatsRepository.GlobalStatsMeta(
                                newDocumentCount,
                                newAverageLength
                        )
                );
            } catch (RuntimeException e) {
                statsRepository.invalidateGlobalStats(
                        statsCollection
                );

                throw e;
            }
        }
    }

    public void removeDocument(
            String statsCollection,
            String documentId
    ) {
        synchronized (lockFor(statsCollection)) {
            Optional<BM25StatsRepository.DocumentStats> oldStatsOptional =
                    statsRepository.loadDocumentStats(
                            statsCollection,
                            documentId
                    );

            if (oldStatsOptional.isEmpty()) {
                log.warn(
                        "BM25-статистика документа {} не найдена",
                        documentId
                );
                return;
            }

            BM25StatsRepository.DocumentStats oldStats =
                    oldStatsOptional.get();

            BM25StatsRepository.GlobalStatsMeta global =
                    statsRepository.loadGlobalStats(statsCollection)
                            .orElse(
                                    new BM25StatsRepository.GlobalStatsMeta(
                                            0,
                                            0.0
                                    )
                            );

            int newDocumentCount = Math.max(
                    0,
                    global.totalDocuments() - 1
            );

            double oldTotalLength =
                    global.totalDocuments()
                            * global.avgDocumentLength();

            double newTotalLength = Math.max(
                    0.0,
                    oldTotalLength - oldStats.length()
            );

            double newAverageLength =
                    newDocumentCount == 0
                            ? 0.0
                            : newTotalLength
                            / newDocumentCount;

            Map<String, Integer> frequencies =
                    statsRepository.loadTermFrequenciesBatch(
                            statsCollection,
                            oldStats.terms()
                    );

            List<PointStruct> pointsToUpsert =
                    new ArrayList<>();

            List<String> pointsToDelete =
                    new ArrayList<>();

            for (String term : oldStats.terms()) {
                int newFrequency = Math.max(
                        0,
                        frequencies.getOrDefault(term, 0) - 1
                );

                if (newFrequency == 0) {
                    pointsToDelete.add(
                            statsRepository.getTermPointId(term)
                    );
                } else {
                    pointsToUpsert.add(
                            statsRepository
                                    .buildTermFrequencyPoint(
                                            term,
                                            newFrequency
                                    )
                    );
                }
            }

            pointsToUpsert.add(
                    statsRepository.buildGlobalStatsPoint(
                            newDocumentCount,
                            newAverageLength
                    )
            );

            pointsToDelete.add(
                    statsRepository.getDocumentStatsPointId(
                            documentId
                    )
            );

            try {
                statsRepository.upsertPointsBatch(
                        statsCollection,
                        pointsToUpsert
                );

                statsRepository.invalidateGlobalStats(
                        statsCollection
                );

                statsRepository.deletePointsBatch(
                        statsCollection,
                        pointsToDelete
                );

                statsRepository.cacheGlobalStats(
                        statsCollection,
                        new BM25StatsRepository.GlobalStatsMeta(
                                newDocumentCount,
                                newAverageLength
                        )
                );
            } catch (RuntimeException e) {
                statsRepository.invalidateGlobalStats(
                        statsCollection
                );

                throw e;
            }
        }
    }

    public void clearCollectionStats(
            String statsCollection
    ) {
        statsRepository.deleteCollectionStats(
                statsCollection
        );
    }

    public Map<Integer, Float> tokenizeWithBM25(
            String statsCollection,
            String text,
            String documentId,
            boolean isDocument
    ) {
        List<String> terms = tokenizeToTerms(text);

        if (terms.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> termFrequency =
                new HashMap<>();

        for (String term : terms) {
            termFrequency.merge(
                    term,
                    1,
                    Integer::sum
            );
        }

        Map<String, Integer> documentFrequencies =
                statsRepository.loadTermFrequenciesBatch(
                        statsCollection,
                        termFrequency.keySet()
                );

        BM25StatsRepository.GlobalStatsMeta global =
                statsRepository.loadGlobalStats(statsCollection)
                        .orElse(
                                new BM25StatsRepository.GlobalStatsMeta(
                                        1,
                                        1.0
                                )
                        );

        int totalDocuments = Math.max(
                global.totalDocuments(),
                1
        );

        double averageDocumentLength = Math.max(
                global.avgDocumentLength(),
                1.0
        );

        int documentLength = isDocument
                ? terms.size()
                : 0;

        Map<Integer, Float> sparseVector =
                new HashMap<>();

        for (Map.Entry<String, Integer> entry
                : termFrequency.entrySet()) {

            String term = entry.getKey();
            int frequency = entry.getValue();

            int documentFrequency =
                    documentFrequencies.getOrDefault(
                            term,
                            0
                    );

            double idf = Math.log(
                    (totalDocuments
                            - documentFrequency
                            + 0.5)
                            / (documentFrequency + 0.5)
                            + 1.0
            );

            float score;

            if (isDocument) {
                double normalizedTermFrequency =
                        (frequency * (K1 + 1.0))
                                / (
                                frequency
                                        + K1 * (
                                        1.0
                                                - B
                                                + B
                                                * documentLength
                                                / averageDocumentLength
                                )
                        );

                score = (float) (
                        idf * normalizedTermFrequency
                );
            } else {
                score = (float) idf;
            }

            if (score <= 0) {
                continue;
            }

            int index = Math.floorMod(
                    term.hashCode(),
                    100_000
            );

            sparseVector.merge(
                    index,
                    score,
                    Float::sum
            );
        }

        return sparseVector;
    }

    public List<String> tokenizeToTerms(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] words = text
                .toLowerCase()
                .replaceAll(
                        "[^a-zA-Zа-яА-Я0-9\\s]",
                        " "
                )
                .split("\\s+");

        List<String> terms = new ArrayList<>();

        for (String word : words) {
            if (word.length() > 2
                    && !STOP_WORDS.contains(word)) {
                terms.add(stem(word));
            }
        }

        return terms;
    }

    private Object lockFor(String statsCollection) {
        return collectionLocks.computeIfAbsent(
                statsCollection,
                ignored -> new Object()
        );
    }

    private String stem(String word) {
        if (word.endsWith("ами")
                || word.endsWith("ями")
                || word.endsWith("ыми")) {
            return word.substring(
                    0,
                    word.length() - 3
            );
        }

        if (word.endsWith("ого")
                || word.endsWith("его")) {
            return word.substring(
                    0,
                    word.length() - 3
            );
        }

        if (word.endsWith("ому")
                || word.endsWith("ему")) {
            return word.substring(
                    0,
                    word.length() - 3
            );
        }

        if (word.endsWith("ах")
                || word.endsWith("ях")) {
            return word.substring(
                    0,
                    word.length() - 2
            );
        }

        if (word.endsWith("ов")
                || word.endsWith("ев")) {
            return word.substring(
                    0,
                    word.length() - 2
            );
        }

        if (word.endsWith("ий")
                || word.endsWith("ый")
                || word.endsWith("ая")
                || word.endsWith("ое")) {
            return word.substring(
                    0,
                    word.length() - 2
            );
        }

        if (word.endsWith("ть")
                || word.endsWith("ти")) {
            return word.substring(
                    0,
                    word.length() - 2
            );
        }

        if (word.endsWith("ет")
                || word.endsWith("ит")
                || word.endsWith("ат")
                || word.endsWith("ят")) {
            return word.substring(
                    0,
                    word.length() - 2
            );
        }

        if (word.endsWith("а")
                || word.endsWith("я")
                || word.endsWith("е")
                || word.endsWith("и")
                || word.endsWith("ы")) {
            return word.length() > 4
                    ? word.substring(
                    0,
                    word.length() - 1
            )
                    : word;
        }

        if (word.endsWith("ing")
                && word.length() > 5) {
            return word.substring(
                    0,
                    word.length() - 3
            );
        }

        if (word.endsWith("tion")
                && word.length() > 6) {
            return word.substring(
                    0,
                    word.length() - 4
            );
        }

        if (word.endsWith("ed")
                && word.length() > 4) {
            return word.substring(
                    0,
                    word.length() - 2
            );
        }

        if (word.endsWith("es")
                && word.length() > 4) {
            return word.substring(
                    0,
                    word.length() - 2
            );
        }

        if (word.endsWith("s")
                && word.length() > 3) {
            return word.substring(
                    0,
                    word.length() - 1
            );
        }

        return word;
    }
}