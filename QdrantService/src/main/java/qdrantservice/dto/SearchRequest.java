package qdrantservice.dto;

public class SearchRequest {
    private String query;
    private int limit = 5;
    private String searchType = "vector"; // "vector", "keyword", "hybrid"
    private float threshold = 0.79f;

    public SearchRequest() {}

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public String getSearchType() { return searchType; }
    public void setSearchType(String searchType) { this.searchType = searchType; }
    public float getThreshold() { return threshold; }
    public void setThreshold(float threshold) { this.threshold = threshold; }
}