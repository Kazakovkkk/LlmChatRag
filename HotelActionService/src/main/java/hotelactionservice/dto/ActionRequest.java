package hotelactionservice.dto;

import java.util.Map;

public class ActionRequest {
    private String hotelKey;
    private String chatId;
    private String actionName;
    private Map<String, String> parameters;

    public ActionRequest() {}

    // Геттеры и сеттеры
    public String getHotelKey() { return hotelKey; }
    public void setHotelKey(String hotelKey) { this.hotelKey = hotelKey; }
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }
    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }
}