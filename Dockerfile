# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-25 AS build

ARG MODULE

WORKDIR /workspace

COPY ${MODULE}/pom.xml ./pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode dependency:go-offline

COPY ${MODULE}/src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode clean package -DskipTests

FROM eclipse-temurin:25-jre

RUN useradd \
    --system \
    --create-home \
    --uid 10001 \
    spring

WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/application.jar

USER spring

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/application.jar"]