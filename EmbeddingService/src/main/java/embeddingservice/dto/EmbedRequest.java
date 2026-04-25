package embeddingservice.dto;

public class EmbedRequest {
    private String text;
    public EmbedRequest() {}
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}