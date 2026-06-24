package LlmChatRag.grpc;

import LlmChatRag.dto.AnswerRequest;
import LlmChatRag.dto.MessageDto;
import LlmChatRag.dto.PreprocessedQuestion;
import com.example.grpc.llm.AnswerStreamRequest;
import com.example.grpc.llm.AnswerStreamResponse;
import com.example.grpc.llm.LlmServiceGrpc;
import com.example.grpc.llm.MessageProto;
import com.example.grpc.llm.PreprocessGrpcRequest;
import com.example.grpc.llm.PreprocessGrpcResponse;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LlmGrpcClient {

    private final LlmServiceGrpc.LlmServiceStub asyncStub;
    private final LlmServiceGrpc.LlmServiceBlockingStub blockingStub;

    public LlmGrpcClient(GrpcChannelFactory channelFactory) {
        ManagedChannel channel =
                channelFactory.createChannel("llm-service");

        this.asyncStub = LlmServiceGrpc.newStub(channel);
        this.blockingStub = LlmServiceGrpc.newBlockingStub(channel);
    }

    public Flux<String> answerStream(AnswerRequest request) {
        List<MessageProto> history = toProtoHistory(
                request.getHistory()
        );

        AnswerStreamRequest grpcRequest =
                AnswerStreamRequest.newBuilder()
                        .setQuestion(nullToEmpty(request.getQuestion()))
                        .setContext(nullToEmpty(request.getContext()))
                        .setTimestamp(nullToEmpty(request.getTimestamp()))
                        .addAllHistory(history)
                        .build();

        return Flux.create(sink ->
                asyncStub
                        .withDeadlineAfter(60, TimeUnit.SECONDS)
                        .answerStream(
                                grpcRequest,
                                new StreamObserver<>() {
                                    @Override
                                    public void onNext(
                                            AnswerStreamResponse response
                                    ) {
                                        if (!sink.isCancelled()) {
                                            sink.next(response.getToken());
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable throwable) {
                                        log.error(
                                                "LLM gRPC stream error: {}",
                                                throwable.getMessage()
                                        );

                                        if (!sink.isCancelled()) {
                                            sink.error(throwable);
                                        }
                                    }

                                    @Override
                                    public void onCompleted() {
                                        if (!sink.isCancelled()) {
                                            sink.complete();
                                        }
                                    }
                                }
                        )
        );
    }

    public PreprocessedQuestion preprocessQuestion(
            String question,
            List<MessageDto> history
    ) {
        PreprocessGrpcRequest request =
                PreprocessGrpcRequest.newBuilder()
                        .setQuestion(nullToEmpty(question))
                        .addAllHistory(toProtoHistory(history))
                        .build();

        PreprocessGrpcResponse response = blockingStub
                .withDeadlineAfter(15, TimeUnit.SECONDS)
                .preprocessQuestion(request);

        PreprocessedQuestion result = new PreprocessedQuestion();

        result.setIntentType(
                response.getIntentType().isBlank()
                        ? "SEARCH"
                        : response.getIntentType()
        );

        result.setActionName(
                response.getActionName().isBlank()
                        ? null
                        : response.getActionName()
        );

        result.setParameters(response.getParametersMap());

        result.setNormalized(
                response.getNormalized().isBlank()
                        ? question
                        : response.getNormalized()
        );

        result.setAlternatives(response.getAlternativesList());

        return result;
    }

    private List<MessageProto> toProtoHistory(
            List<MessageDto> history
    ) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        return history.stream()
                .filter(message -> message != null)
                .map(message ->
                        MessageProto.newBuilder()
                                .setRole(nullToEmpty(message.getRole()))
                                .setContent(nullToEmpty(message.getContent()))
                                .build()
                )
                .toList();
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}