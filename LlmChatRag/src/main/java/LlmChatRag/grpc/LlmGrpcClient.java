// grpc/LlmGrpcClient.java
package LlmChatRag.grpc;

import com.example.grpc.llm.AnswerStreamRequest;
import com.example.grpc.llm.AnswerStreamResponse;
import com.example.grpc.llm.LlmServiceGrpc;
import com.example.grpc.llm.MessageProto;
import LlmChatRag.dto.AnswerRequest;
import io.grpc.ManagedChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LlmGrpcClient {

    private final LlmServiceGrpc.LlmServiceStub stub;

    public LlmGrpcClient(GrpcChannelFactory channelFactory) {
        ManagedChannel channel = channelFactory.createChannel("llm-service");
        this.stub = LlmServiceGrpc.newStub(channel);
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
}