package LlmChatRag.service;

import LlmChatRag.dto.ActionRequest;
import LlmChatRag.dto.ActionResponse;
import LlmChatRag.grpc.HotelActionGrpcClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class HotelActionRouter {

    private final HotelActionGrpcClient actionGrpcClient;
    private final RestClient actionRestClient;

    @Value("${hotel.action.protocol:grpc}") // Переключатель протокола для интеграций с отелем
    private String protocol;

    public HotelActionRouter(HotelActionGrpcClient actionGrpcClient,
                             @Value("${hotel.action.service.url:http://localhost:8089}") String actionServiceUrl) {
        this.actionGrpcClient = actionGrpcClient;
        this.actionRestClient = RestClient.builder().baseUrl(actionServiceUrl).build();
    }

    public ActionResponse execute(ActionRequest request) {
        //log.info("⏱ Маршрутизация действия отеля [Action: {}, Protocol: {}]", request.getActionName(), protocol.toUpperCase());

        if ("grpc".equalsIgnoreCase(protocol)) {
            return actionGrpcClient.executeAction(request);
        } else {
            return actionRestClient.post()
                    .uri("/api/actions/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ActionResponse.class);
        }
    }
}