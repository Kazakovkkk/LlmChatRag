package llmservice.dto;

public class MessageDto {
    private String role;
    private String content;

    public MessageDto() {}
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}