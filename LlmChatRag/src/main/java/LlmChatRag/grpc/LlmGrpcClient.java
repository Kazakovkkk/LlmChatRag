// grpc/LlmGrpcClient.java
package LlmChatRag.grpc;

import com.example.grpc.llm.*;
import LlmChatRag.dto.AnswerRequest;
import LlmChatRag.dto.PreprocessedQuestion;
import LlmChatRag.dto.MessageDto;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LlmGrpcClient {

    private final LlmServiceGrpc.LlmServiceStub stub;
    private final LlmServiceGrpc.LlmServiceBlockingStub blockingStub; // ← Добавляем блокирующий стаб для унарных вызовов

    public LlmGrpcClient(GrpcChannelFactory channelFactory) {
        ManagedChannel channel = channelFactory.createChannel("llm-service");
        this.stub = LlmServiceGrpc.newStub(channel);
        this.blockingStub = LlmServiceGrpc.newBlockingStub(channel);
    }
    public Flux<String> answerStream(AnswerRequest request) {
        long start = System.currentTimeMillis();
        log.info("⏱ gRPC answerStream начало | вопрос: '{}'", request.getQuestion());

        List<MessageProto> historyProto = request.getHistory() != null
                ? request.getHistory().stream()
                .map(m -> MessageProto.newBuilder()
                        .setRole(m.getRole())
                        .setContent(m.getContent())
                        .build())
                .collect(Collectors.toList())
                : List.of();

        AnswerStreamRequest grpcRequest = AnswerStreamRequest.newBuilder()
                .setQuestion(request.getQuestion())
                .setContext(request.getContext())
                .addAllHistory(historyProto)
                .setTimestamp(request.getTimestamp() != null ? request.getTimestamp() : "")
                .build();

        return Flux.create(sink -> {
            stub.answerStream(grpcRequest, new io.grpc.stub.StreamObserver<AnswerStreamResponse>() {
                @Override
                public void onNext(AnswerStreamResponse response) {
                    sink.next(response.getToken());
                }

                @Override
                public void onError(Throwable t) {
                    log.error("⏱ gRPC answerStream ошибка | {} мс | {}",
                            System.currentTimeMillis() - start, t.getMessage());
                    sink.error(t);
                }

                @Override
                public void onCompleted() {
                    log.info("⏱ gRPC answerStream завершён | {} мс",
                            System.currentTimeMillis() - start);
                    sink.complete();
                }
            });
        });
    }
    public PreprocessedQuestion preprocessQuestion(String question, List<MessageDto> history) {
        List<MessageProto> historyProto = history.stream()
                .map(m -> MessageProto.newBuilder()
                        .setRole(m.getRole())
                        .setContent(m.getContent())
                        .build())
                .collect(Collectors.toList());

        PreprocessGrpcRequest grpcRequest = PreprocessGrpcRequest.newBuilder()
                .setQuestion(question)
                .addAllHistory(historyProto)
                .build();

        // Выполняем блокирующий сетевой запрос к llmservice по gRPC
        PreprocessGrpcResponse response = blockingStub.preprocessQuestion(grpcRequest);

        PreprocessedQuestion dto = new PreprocessedQuestion();
        dto.setIntentType(response.getIntentType());
        dto.setActionName(response.getActionName().isEmpty() ? null : response.getActionName());
        dto.setParameters(response.getParametersMap());
        dto.setNormalized(response.getNormalized().isEmpty() ? null : response.getNormalized());
        dto.setAlternatives(response.getAlternativesList());

        return dto;
    }
}