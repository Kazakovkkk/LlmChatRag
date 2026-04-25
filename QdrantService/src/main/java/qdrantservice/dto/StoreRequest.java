package qdrantservice.dto;

import java.util.List;

public class StoreRequest {
    private String text;
    private List<String> tags;

    public StoreRequest() {}
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}