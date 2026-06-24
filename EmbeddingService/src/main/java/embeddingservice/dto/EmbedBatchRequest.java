package embeddingservice.dto;

import java.util.List;

public class EmbedBatchRequest {

    private List<String> texts = List.of();
    private String inputType = "query";

    public EmbedBatchRequest() {
    }

    public List<String> getTexts() {
        return texts;
    }

    public void setTexts(List<String> texts) {
        this.texts = texts;
    }

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }
}