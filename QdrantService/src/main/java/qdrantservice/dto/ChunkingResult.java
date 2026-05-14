package qdrantservice.dto;

import java.util.List;

public record ChunkingResult(
        List<String> chunks,
        List<String> sentences,
        List<float[]> embeddings
) {}