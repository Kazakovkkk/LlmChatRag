package adminpanel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {
    private String id;                  // UUID чанка из Qdrant
    private String text;                // Очищенный текстовый сегмент
    private Double score;               // Релевантность (используется при поиске)
    private Map<String, Object> metadata; // Метаданные (теги, источник)
}