package adminpanel.grpc;

import adminpanel.dto.DocumentDto;
import com.example.grpc.GetChunksRequest;
import com.example.grpc.GetChunksResponse;
import com.example.grpc.RagServiceGrpc;
import com.example.grpc.UpdateChunkRequest;
import com.example.grpc.UpdateChunkResponse;
import com.example.grpc.UploadRequest;
import com.example.grpc.UploadResponse;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RagGrpcClient {

    private final RagServiceGrpc.RagServiceBlockingStub stub;

    public RagGrpcClient(GrpcChannelFactory channelFactory) {
        // Подключаемся к каналу, настроенному в application.yml под именем 'rag-service'
        ManagedChannel channel = channelFactory.createChannel("rag-service");
        this.stub = RagServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Запрос списка всех текстовых сегментов отеля из Qdrant
     */
    public List<DocumentDto> fetchChunks(String hotelKey, int limit) {
        log.info("📤 Отправка gRPC запроса GetChunks | Отель: {}, Лимит: {}", hotelKey, limit);

        GetChunksRequest request = GetChunksRequest.newBuilder()
                .setHotelKey(hotelKey)
                .setLimit(limit)
                .build();

        try {
            GetChunksResponse response = stub.getChunks(request);
            List<DocumentDto> resultList = new ArrayList<>();

            response.getChunksList().forEach(proto -> {
                DocumentDto dto = DocumentDto.builder()
                        .id(proto.getId())
                        .text(proto.getText())
                        .score(proto.getScore())
                        .metadata(new HashMap<>(proto.getMetadataMap()))
                        .build();
                resultList.add(dto);
            });

            log.info("📥 Получен gRPC ответ GetChunks | Извлечено элементов: {}", resultList.size());
            return resultList;

        } catch (Exception e) {
            log.error("Критический сбой gRPC канала при выгрузке чанков для {}: {}", hotelKey, e.getMessage());
            throw new RuntimeException("Ошибка взаимодействия векторов через gRPC: " + e.getMessage(), e);
        }
    }

    /**
     * Точечное изменение текстового payload и пересчет вектора
     */
    public boolean updateChunk(String hotelKey, String chunkId, String newText) {
        log.info("📤 Отправка gRPC мутации UpdateChunk | Отель: {}, ID чанка: {}", hotelKey, chunkId);

        UpdateChunkRequest request = UpdateChunkRequest.newBuilder()
                .setHotelKey(hotelKey)
                .setChunkId(chunkId)
                .setNewText(newText)
                .build();

        try {
            UpdateChunkResponse response = stub.updateChunk(request);
            log.info("📥 Получен gRPC ответ UpdateChunk | Статус операции: {}", response.getSuccess());
            return response.getSuccess();
        } catch (Exception e) {
            log.error("Критический сбой gRPC мутации для точки {}: {}", chunkId, e.getMessage());
            return false;
        }
    }

    /**
     * Загрузка PDF файла в изолированную структуру отеля
     */
    public boolean uploadDocument(String hotelKey, byte[] pdfBytes, String filename, String mode) {
        log.info("📤 Отправка gRPC бинарного пакета UploadDocument | Отель: {}, Режим: {}, Файл: '{}'", hotelKey, mode, filename);

        UploadRequest request = UploadRequest.newBuilder()
                .setHotelKey(hotelKey)
                .setPdfData(com.google.protobuf.ByteString.copyFrom(pdfBytes))
                .setFilename(filename)
                .setMode(mode) // <-- ДОБАВЛЕНО: передача режима в Protobuf объект
                .build();

        try {
            UploadResponse response = stub.uploadDocument(request);
            log.info("📥 Получен gRPC ответ UploadDocument | Результат: {}, Сообщение: {}",
                    response.getSuccess(), response.getMessage());
            return response.getSuccess();
        } catch (Exception e) {
            log.error("Критический сбой gRPC при передаче файла '{}' в режиме '{}': {}", filename, mode, e.getMessage());
            return false;
        }
    }
}