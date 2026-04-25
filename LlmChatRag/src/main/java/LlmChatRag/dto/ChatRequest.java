package LlmChatRag.dto;

import java.util.List;

public class ChatRequest {
    private String message;
    private List<MessageDto> history;
    private String timestamp;

    public ChatRequest() {}
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<MessageDto> getHistory() { return history; }
    public void setHistory(List<MessageDto> history) { this.history = history; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp;}
    }