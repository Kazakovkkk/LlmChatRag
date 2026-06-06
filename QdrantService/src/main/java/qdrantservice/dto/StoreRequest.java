package qdrantservice.dto;

import java.util.List;

public class StoreRequest {
    private String hotelKey; // Обязательный ключ отеля
    private String text;
    private List<String> tags;

    public StoreRequest() {}
    public String getHotelKey() { return hotelKey; }
    public void setHotelKey(String hotelKey) { this.hotelKey = hotelKey; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}