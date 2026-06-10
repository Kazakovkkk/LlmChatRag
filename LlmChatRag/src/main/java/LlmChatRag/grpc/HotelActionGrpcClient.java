package LlmChatRag.grpc;

import LlmChatRag.dto.ActionRequest;
import LlmChatRag.dto.ActionResponse;
import com.example.grpc.action.HotelActionServiceGrpc; // Импортируем только сам сервис
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HotelActionGrpcClient {

    private final HotelActionServiceGrpc.HotelActionServiceBlockingStub blockingStub;

    public HotelActionGrpcClient(GrpcChannelFactory channelFactory) {
        ManagedChannel channel = channelFactory.createChannel("hotel-action-service");
        this.blockingStub = HotelActionServiceGrpc.newBlockingStub(channel);
    }

    public ActionResponse executeAction(ActionRequest request) {
        // Указываем полный путь к классу Protobuf, чтобы Java не перепутала его с DTO
        com.example.grpc.action.ActionRequest grpcRequest = com.example.grpc.action.ActionRequest.newBuilder()
                .setHotelKey(request.getHotelKey())
                .setChatId(request.getChatId())
                .setActionName(request.getActionName())
                // ДОБАВЛЯЕМ ЗАЩИТУ: если параметры null, передаем пустую карту
                .putAllParameters(request.getParameters() != null ? request.getParameters() : java.util.Map.of())
                .build();

        // Вызываем gRPC метод и получаем gRPC ответ (тоже через полный путь к классу)
        com.example.grpc.action.ActionResponse response = blockingStub.executeAction(grpcRequest);

        // Маппим данные в наше чистое Java DTO для отправки в сервис оркестратора
        ActionResponse dto = new ActionResponse();
        dto.setSuccess(response.getSuccess());
        dto.setStatus(response.getStatus());
        dto.setMessage(response.getMessage());
        dto.setDetails(response.getDetailsMap());
        return dto;
    }
}