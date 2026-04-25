package qdrantservice.dto;

import java.util.List;

public record ChunkingResult(
        List<String> chunks,
        List<String> sentences,   // Может быть пустым для fixed/recursive
        List<float[]> embeddings  // Может быть пустым для fixed/recursive
) {}