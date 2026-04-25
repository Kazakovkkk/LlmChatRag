package llmservice.dto;

import java.util.List;

public class PreprocessedQuestion {
    private String normalized;
    private List<String> alternatives;

    // Конструкторы
    public PreprocessedQuestion() {}

    public PreprocessedQuestion(String normalized, List<String> alternatives) {
        this.normalized = normalized;
        this.alternatives = alternatives;
    }

    // Геттеры и сеттеры
    public String getNormalized() { return normalized; }
    public void setNormalized(String normalized) { this.normalized = normalized; }

    public List<String> getAlternatives() { return alternatives; }
    public void setAlternatives(List<String> alternatives) { this.alternatives = alternatives; }
}