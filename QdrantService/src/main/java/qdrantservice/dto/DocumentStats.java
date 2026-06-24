package qdrantservice.dto;

import java.util.Set;

public record DocumentStats(
        int length,
        Set<String> terms
) {}
