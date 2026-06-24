package load;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class HealthSmokeSimulation extends Simulation {

    private final String baseUrl =
            System.getProperty("baseUrl", "http://localhost:8080");

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(baseUrl)
            .acceptHeader("application/json")
            .userAgentHeader("Gatling load test");

    private final ScenarioBuilder scenario = scenario("Hotel service health")
            .exec(
                    http("GET chat health")
                            .get("/api/chat/health")
                            .check(status().is(200))
                            .check(bodyString().is("Hotel service is running!"))
            );

    {
        setUp(
                scenario.injectOpen(atOnceUsers(1))
        )
                .protocols(httpProtocol)
                .assertions(
                        global().failedRequests().count().is(0L),
                        global().responseTime().max().lt(2000)
                );
    }
}