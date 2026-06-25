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
        com.example.grpc.action.ActionRequest grpcRequest = com.example.grpc.action.ActionRequest.newBuilder()
                .setHotelKey(request.getHotelKey())
                .setChatId(request.getChatId())
                .setActionName(request.getActionName())
                .putAllParameters(request.getParameters() != null ? request.getParameters() : java.util.Map.of())
                .build();

        com.example.grpc.action.ActionResponse response = blockingStub.executeAction(grpcRequest);

        ActionResponse dto = new ActionResponse();
        dto.setSuccess(response.getSuccess());
        dto.setStatus(response.getStatus());
        dto.setMessage(response.getMessage());
        dto.setDetails(response.getDetailsMap());
        return dto;
    }
}