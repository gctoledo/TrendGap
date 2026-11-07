# syntax=docker/dockerfile:1
# Build context is the repository root (the OpenAPI client is generated from ../contracts):
#   docker build -f backend/Dockerfile -t trendgap-backend:test .
FROM eclipse-temurin:25.0.5_8-jdk-noble AS build
WORKDIR /workspace
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml backend/
COPY contracts contracts
WORKDIR /workspace/backend
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY backend/docker/HealthCheck.java /healthcheck/HealthCheck.java
RUN javac -d /healthcheck /healthcheck/HealthCheck.java
COPY backend/src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/trendgap-*.jar extract --layers --destination /extracted

FROM eclipse-temurin:25.0.5_8-jre-noble
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
COPY --from=build /healthcheck/HealthCheck.class /app/HealthCheck.class
USER 10001:10001
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/trendgap-0.0.1-SNAPSHOT.jar"]
