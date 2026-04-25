package llmservice.dto;

import java.util.List;

public class PreprocessRequest {
    private String question;
    private List<MessageDto> history;  // ← добавили

    public PreprocessRequest() {}
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<MessageDto> getHistory() { return history; }
    public void setHistory(List<MessageDto> history) { this.history = history; }
}