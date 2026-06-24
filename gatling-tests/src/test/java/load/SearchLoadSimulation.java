package load;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.util.UUID;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class SearchLoadSimulation extends Simulation {

    private final String baseUrl = System.getProperty(
            "baseUrl",
            "http://localhost:8085"
    );

    private final double usersPerSecond = Double.parseDouble(
            System.getProperty("usersPerSecond", "1")
    );

    private final int rampSeconds = Integer.parseInt(
            System.getProperty("rampSeconds", "30")
    );

    private final int durationSeconds = Integer.parseInt(
            System.getProperty("durationSeconds", "60")
    );

    private final String[] queries = {
            "Есть ли в отеле бассейн?",
            "Во сколько начинается завтрак?",
            "Есть ли парковка?",
            "Как подключиться к Wi-Fi?",
            "Можно ли заказать уборку номера?",
            "Когда работает ресторан?"
    };

    private final Iterator<Map<String, Object>> feeder =
            Stream.generate(() -> {
                int index = ThreadLocalRandom.current()
                        .nextInt(queries.length);

                return Map.<String, Object>of(
                        "hotelKey", "cosmos",
                        "query", queries[index]
                );
            }).iterator();

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder searchScenario = scenario(
            "Hybrid search"
    )
            .feed(feeder)
            .exec(
                    http("POST /api/incidents/similar")
                            .post("/api/incidents/similar")
                            .body(
                                    StringBody("""
                                            {
                                              "hotelKey": "#{hotelKey}",
                                              "query": "#{query}",
                                              "limit": 5,
                                              "searchType": "hybrid"
                                            }
                                            """)
                            )
                            .asJson()
                            .check(status().is(200))
                            .check(bodyString().notNull())
            );

    {
        setUp(
                searchScenario.injectOpen(
                        rampUsersPerSec(0.1)
                                .to(usersPerSecond)
                                .during(
                                        Duration.ofSeconds(rampSeconds)
                                ),

                        constantUsersPerSec(usersPerSecond)
                                .during(
                                        Duration.ofSeconds(durationSeconds)
                                )
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        global()
                                .failedRequests()
                                .percent()
                                .lt(1.0),

                        global()
                                .responseTime()
                                .percentile3()
                                .lt(5_000)
                );
    }
}