package llmservice.grpc;

import com.example.grpc.llm.*;
import llmservice.dto.MessageDto;
import llmservice.dto.PreprocessedQuestion;
import llmservice.service.AnswerGenerationService;
import llmservice.service.QueryPreprocessorService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
public class LlmGrpcService extends LlmServiceGrpc.LlmServiceImplBase {

    private final AnswerGenerationService answerGenerationService;
    private final QueryPreprocessorService queryPreprocessorService;

    public LlmGrpcService(AnswerGenerationService answerGenerationService,
                          QueryPreprocessorService queryPreprocessorService) {
        this.answerGenerationService = answerGenerationService;
        this.queryPreprocessorService = queryPreprocessorService;
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
    @Override
    public void preprocessQuestion(PreprocessGrpcRequest request,
                                   StreamObserver<PreprocessGrpcResponse> responseObserver) {
        long start = System.currentTimeMillis();
        log.info("⏱ gRPC preprocessQuestion начало | Вопрос: '{}'", request.getQuestion());

        List<MessageDto> history = request.getHistoryList().stream()
                .map(m -> {
                    MessageDto dto = new MessageDto();
                    dto.setRole(m.getRole());
                    dto.setContent(m.getContent());
                    return dto;
                })
                .collect(Collectors.toList());

        // Вызываем реактивный препроцессор
        queryPreprocessorService.preprocessQuestion(request.getQuestion(), history)
                .subscribe(
                        processed -> {
                            PreprocessGrpcResponse.Builder responseBuilder = PreprocessGrpcResponse.newBuilder()
                                    .setIntentType(processed.getIntentType() != null ? processed.getIntentType() : "SEARCH")
                                    .setActionName(processed.getActionName() != null ? processed.getActionName() : "")
                                    .setNormalized(processed.getNormalized() != null ? processed.getNormalized() : "");

                            if (processed.getParameters() != null) {
                                responseBuilder.putAllParameters(processed.getParameters());
                            }

                            if (processed.getAlternatives() != null) {
                                responseBuilder.addAllAlternatives(processed.getAlternatives());
                            }

                            log.info("gRPC preprocessQuestion успешно завершен за {} мс | Интент: {}",
                                    System.currentTimeMillis() - start, processed.getIntentType());

                            responseObserver.onNext(responseBuilder.build());
                            responseObserver.onCompleted();
                        },
                        error -> {
                            log.error("Ошибка gRPC preprocessQuestion | {} мс | {}",
                                    System.currentTimeMillis() - start, error.getMessage());

                            PreprocessGrpcResponse fallbackResponse = PreprocessGrpcResponse.newBuilder()
                                    .setIntentType("SEARCH")
                                    .setNormalized(request.getQuestion())
                                    .addAllAlternatives(List.of(request.getQuestion()))
                                    .build();

                            responseObserver.onNext(fallbackResponse);
                            responseObserver.onCompleted();
                        }
                );
    }
}