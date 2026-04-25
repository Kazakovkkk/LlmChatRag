// com/example/LlmChatRag/dto/DocumentDto.java
package LlmChatRag.dto;

import java.util.Map;

public class DocumentDto {
    private String id;
    private String text;
    private Double score;
    private Map<String, Object> metadata;

    public DocumentDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}