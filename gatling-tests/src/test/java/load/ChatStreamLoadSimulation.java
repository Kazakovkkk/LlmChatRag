package load;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;
/*
docker run --rm -it `
  --add-host=host.docker.internal:host-gateway `
  -v "${PWD}:/workspace" `
  -v gatling-m2:/root/.m2 `
  -w /workspace `
  maven:3.9-eclipse-temurin-25 `
  mvn gatling:test `
  "-Dgatling.simulationClass=load.ChatStreamLoadSimulation" `
  "-DbaseUrl=http://host.docker.internal:8080" `
  "-DusersPerSecond=3" `
  "-DrampSeconds=30" `
  "-DdurationSeconds=120" `
  "-DfirstTokenTimeoutSeconds=30" `
  "-DstreamHoldSeconds=15"
*/
public class ChatStreamLoadSimulation extends Simulation {

    private final String baseUrl = System.getProperty(
            "baseUrl",
            "http://localhost:8080"
    );

    private final double usersPerSecond = Double.parseDouble(
            System.getProperty("usersPerSecond", "0.5")
    );

    private final int rampSeconds = Integer.parseInt(
            System.getProperty("rampSeconds", "30")
    );

    private final int durationSeconds = Integer.parseInt(
            System.getProperty("durationSeconds", "60")
    );

    private final int firstTokenTimeoutSeconds = Integer.parseInt(
            System.getProperty("firstTokenTimeoutSeconds", "30")
    );

    private final int streamHoldSeconds = Integer.parseInt(
            System.getProperty("streamHoldSeconds", "10")
    );

    private final String[] questions = {
            "Есть ли в отеле бассейн?",
            "Во сколько начинается завтрак?",
            "Есть ли парковка?",
            "Как подключиться к Wi-Fi?",
            "Можно ли заказать уборку номера?",
            "Когда работает ресторан?",
            "Как работает ваш фитнес-центр и бассейн? Сколько это стоит?",
            "Я планирую заселиться с собакой. Какие у вас правила для животных?",

    };

    private final Iterator<Map<String, Object>> questionFeeder =
            Stream.generate(() -> {
                int index = ThreadLocalRandom.current()
                        .nextInt(questions.length);

                return Map.<String, Object>of(
                        "hotelKey", "cosmos",
                        "question", questions[index]
                );
            }).iterator();

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("text/event-stream")
            .contentTypeHeader("application/json")
            .sseUnmatchedInboundMessageBufferSize(10);

    private final ScenarioBuilder chatStreamScenario =
            scenario("Chat stream")
                    .feed(questionFeeder)
                    .exec(session -> session
                            .set(
                                    "chatId",
                                    UUID.randomUUID().toString()
                            )
                            .set(
                                    "timestamp",
                                    Instant.now().toString()
                            )
                    )
                    .exec(
                            sse("POST /api/chat/stream")
                                    .post("/api/chat/stream")
                                    .header(
                                            "Content-Type",
                                            "application/json"
                                    )
                                    .body(
                                            StringBody("""
                                                    {
                                                      "hotelKey": "#{hotelKey}",
                                                      "chatId": "#{chatId}",
                                                      "message": "#{question}",
                                                      "history": [],
                                                      "timestamp": "#{timestamp}"
                                                    }
                                                    """)
                                    )
                                    .await(firstTokenTimeoutSeconds)
                                    .on(
                                            sse.checkMessage("First token")
                                                    .check(
                                                            jsonPath("$.data")
                                                                    .exists()
                                                    )
                                    )
                    )
                    .pause(
                            Duration.ofSeconds(streamHoldSeconds)
                    )
                    .exec(
                            sse("Close chat stream").close()
                    );

    {
        setUp(
                chatStreamScenario.injectOpen(
                        rampUsersPerSec(0.1)
                                .to(usersPerSecond)
                                .during(
                                        Duration.ofSeconds(
                                                rampSeconds
                                        )
                                ),

                        constantUsersPerSec(usersPerSecond)
                                .during(
                                        Duration.ofSeconds(
                                                durationSeconds
                                        )
                                )
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        global()
                                .failedRequests()
                                .percent()
                                .lt(1.0)
                );
    }
}