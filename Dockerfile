FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B -DskipTests package && mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:2.33.0 -DoutputDirectory=/agent
FROM eclipse-temurin:17-jre
RUN mkdir -p /app /logs && chown -R 10001:10001 /app /logs
WORKDIR /app
COPY --from=build /build/target/incidentlens-0.1.0.jar app.jar
COPY --from=build /agent/opentelemetry-javaagent-2.33.0.jar opentelemetry-javaagent.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
