// grpc/LlmGrpcService.java
package llmservice.grpc;

import com.example.grpc.llm.AnswerStreamRequest;
import com.example.grpc.llm.AnswerStreamResponse;
import com.example.grpc.llm.LlmServiceGrpc;
import llmservice.dto.MessageDto;
import llmservice.service.AnswerGenerationService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
public class LlmGrpcService extends LlmServiceGrpc.LlmServiceImplBase {

    private final AnswerGenerationService answerGenerationService;

    public LlmGrpcService(AnswerGenerationService answerGenerationService) {
        this.answerGenerationService = answerGenerationService;
    }

    @Override
    public void answerStream(AnswerStreamRequest request,
                             StreamObserver<AnswerStreamResponse> responseObserver) {
        long start = System.currentTimeMillis();
        log.info("⏱ gRPC answerStream начало | вопрос: '{}'", request.getQuestion());

        List<MessageDto> history = request.getHistoryList().stream()
                .map(m -> {
                    MessageDto dto = new MessageDto();
                    dto.setRole(m.getRole());
                    dto.setContent(m.getContent());
                    return dto;
                })
                .collect(Collectors.toList());

        answerGenerationService.generateAnswerStream(
                request.getQuestion(),
                request.getContext(),
                history,
                request.getTimestamp()
        ).subscribe(
                token -> {
                    responseObserver.onNext(
                            AnswerStreamResponse.newBuilder()
                                    .setToken(token)
                                    .build()
                    );
                },
                error -> {
                    log.error("⏱ gRPC answerStream ошибка | {} мс | {}",
                            System.currentTimeMillis() - start, error.getMessage());
                    responseObserver.onError(error);
                },
                () -> {
                    log.info("⏱ gRPC answerStream завершён | {} мс",
                            System.currentTimeMillis() - start);
                    responseObserver.onCompleted();
                }
        );
    }
}