package hotelactionservice.grpc;

import com.example.grpc.action.HotelActionServiceGrpc;
import hotelactionservice.dto.ActionResponse;
import hotelactionservice.service.ActionExecutorService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
public class HotelActionGrpcService extends HotelActionServiceGrpc.HotelActionServiceImplBase {

    private final ActionExecutorService actionExecutorService;

    public HotelActionGrpcService(ActionExecutorService actionExecutorService) {
        this.actionExecutorService = actionExecutorService;
    }

    @Override
    public void executeAction(com.example.grpc.action.ActionRequest request,
                              StreamObserver<com.example.grpc.action.ActionResponse> responseObserver) {

        log.info("⏱ gRPC [HotelActionService.executeAction] Начало обработки экшена: {}", request.getActionName());

        // Вызываем центральный сервис бизнес-логики
        ActionResponse businessResponse = actionExecutorService.processAction(
                request.getHotelKey(),
                request.getChatId(),
                request.getActionName(),
                request.getParametersMap()
        );

        // Собираем gRPC Protobuf ответ
        com.example.grpc.action.ActionResponse grpcResponse = com.example.grpc.action.ActionResponse.newBuilder()
                .setSuccess(businessResponse.isSuccess())
                .setStatus(businessResponse.getStatus() != null ? businessResponse.getStatus() : "CONFIRMED")
                .setMessage(businessResponse.getMessage() != null ? businessResponse.getMessage() : "")
                .putAllDetails(businessResponse.getDetails() != null ? businessResponse.getDetails() : java.util.Map.of())
                .build();

        // Отправляем ответ обратно в LlmChatRag оркестратор
        responseObserver.onNext(grpcResponse);
        responseObserver.onCompleted();

        log.info("⏱ gRPC [HotelActionService.executeAction] Успешно завершен");
    }
}