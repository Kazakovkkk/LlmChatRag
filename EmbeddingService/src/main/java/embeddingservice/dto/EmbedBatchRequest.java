package embeddingservice.dto;

import java.util.List;

public class EmbedBatchRequest {
    private List<String> texts;
    public EmbedBatchRequest() {}
    public List<String> getTexts() { return texts; }
    public void setTexts(List<String> texts) { this.texts = texts; }
}