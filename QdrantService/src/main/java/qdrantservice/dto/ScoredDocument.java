package qdrantservice.dto;

public class ScoredDocument {
    private String id; // <-- ДОБАВЛЯЕМ: Реальный UUID точки из Qdrant
    private String text;
    private float score;
    private String searchType;
    private Float vectorScore;
    private Float keywordScore;
    private Float rrfScore;

    // Новый конструктор (для гибридного поиска с поддержкой ID)
    public ScoredDocument(String id, String text, float score, String searchType) {
        this.id = id;
        this.text = text;
        this.score = score;
        this.searchType = searchType;
    }

    // Твой оригинальный конструктор (оставляем, чтобы ничего не сломать)
    public ScoredDocument(String text, float score, String searchType) {
        this.text = text;
        this.score = score;
        this.searchType = searchType;
    }

    // Геттер и сеттер для ID
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    // Твои оригинальные геттеры и сеттеры
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