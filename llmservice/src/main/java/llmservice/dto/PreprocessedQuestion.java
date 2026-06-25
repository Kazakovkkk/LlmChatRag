package llmservice.dto;

import java.util.List;
import java.util.Map;

public class PreprocessedQuestion {
    private String intentType;
    private String actionName;
    private Map<String, String> parameters;


    private String normalized;
    private List<String> alternatives;

    public PreprocessedQuestion() {}

    public PreprocessedQuestion(String intentType, String actionName, Map<String, String> parameters, String normalized, List<String> alternatives) {
        this.intentType = intentType;
        this.actionName = actionName;
        this.parameters = parameters;
        this.normalized = normalized;
        this.alternatives = alternatives;
    }

    public String getIntentType() { return intentType; }
    public void setIntentType(String intentType) { this.intentType = intentType; }

    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }

    public String getNormalized() { return normalized; }
    public void setNormalized(String normalized) { this.normalized = normalized; }

    public List<String> getAlternatives() { return alternatives; }
    public void setAlternatives(List<String> alternatives) { this.alternatives = alternatives; }
}