package qdrantservice.dto;

import java.util.List;

public record BatchSearchRequest(
        String hotelKey,
        List<String> queries,
        Integer limit
) {}