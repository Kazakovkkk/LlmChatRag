package embeddingservice.dto;

public class EmbedRequest {

    private String text;
    private String inputType = "query";

    public EmbedRequest() {
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }
}