package qdrantservice.dto;

import java.util.List;

public class SearchRequest {
    private String hotelKey; // Обязательный ключ отеля
    private String query;
    private int limit = 5;
    private String searchType = "vector"; // "vector", "keyword", "hybrid"
    private float threshold = 0.70f;

    public SearchRequest() {}

    public String getHotelKey() { return hotelKey; }
    public void setHotelKey(String hotelKey) { this.hotelKey = hotelKey; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public String getSearchType() { return searchType; }
    public void setSearchType(String searchType) { this.searchType = searchType; }
    public float getThreshold() { return threshold; }
    public void setThreshold(float threshold) { this.threshold = threshold; }
}