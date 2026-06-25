package LlmChatRag.dto;

public class SearchRequest {
    private String query;
    private int limit = 5;
    // vector, keyword, hybrid
    private String searchType = "hybrid"; //

    public SearchRequest() {}
    public SearchRequest(String query, int limit) {
        this.query = query;
        this.limit = limit;
    }
    public SearchRequest(String query, int limit, String searchType) {
        this.query = query;
        this.limit = limit;
        this.searchType = searchType;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = limit; }
    public String getSearchType() { return searchType; }
    public void setSearchType(String searchType) { this.searchType = searchType; }
}