package qdrantservice.dto;
public class ScoredDocument {
    private String text;
    private float score;
    private String searchType;
    private Float vectorScore;
    private Float keywordScore;
    private Float rrfScore;

    public ScoredDocument(String text, float score, String searchType) {
        this.text = text;
        this.score = score;
        this.searchType = searchType;
    }

    public String getText() { return text; }
    public float getScore() { return score; }
    public String getSearchType() { return searchType; }
    public Float getVectorScore() { return vectorScore; }
    public void setVectorScore(Float vectorScore) { this.vectorScore = vectorScore; }
    public Float getKeywordScore() { return keywordScore; }
    public void setKeywordScore(Float keywordScore) { this.keywordScore = keywordScore; }
    public Float getRrfScore() { return rrfScore; }
    public void setRrfScore(Float rrfScore) { this.rrfScore = rrfScore; }
}