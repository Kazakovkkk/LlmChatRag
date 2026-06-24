package LlmChatRag.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
@Data
public class PreprocessedQuestion {
    private String intentType;
    private String actionName;
    private Map<String, String> parameters;
    private String normalized;
    private List<String> alternatives;

    public PreprocessedQuestion() {}

    // Геттеры и сеттеры
    public String getIntentType() { return intentType; }
    public void setIntentType(String intentType) { this.intentType = intentType; }
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }
    public Map<String, String> getParameters() {
        // Если Jackson записал сюда null, геттер вернет безопасную пустую карту
        return parameters != null ? parameters : java.util.Map.of();
    }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }
    public String getNormalized() { return normalized; }
    public void setNormalized(String normalized) { this.normalized = normalized; }
    public List<String> getAlternatives() { return alternatives; }
    public void setAlternatives(List<String> alternatives) { this.alternatives = alternatives; }
}