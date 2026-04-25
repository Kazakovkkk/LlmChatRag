package llmservice.dto;

import java.util.List;

public class AnswerRequest {
    private String question;
    private String context;
    private List<MessageDto> history;
    private String timestamp;// ← добавили

    public AnswerRequest() {}
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public List<MessageDto> getHistory() { return history; }
    public void setHistory(List<MessageDto> history) { this.history = history; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}