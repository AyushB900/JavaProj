# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=secret,id=maven_settings --mount=type=secret,id=maven_truststore \
    if [ -f /run/secrets/maven_truststore ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/run/secrets/maven_truststore"; fi; \
    if [ -f /run/secrets/maven_settings ]; then mvn -s /run/secrets/maven_settings -B -ntp dependency:go-offline; \
    else mvn -B -ntp dependency:go-offline; fi
COPY src ./src
RUN --mount=type=secret,id=maven_settings --mount=type=secret,id=maven_truststore \
    if [ -f /run/secrets/maven_truststore ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/run/secrets/maven_truststore"; fi; \
    if [ -f /run/secrets/maven_settings ]; then mvn -s /run/secrets/maven_settings -B -ntp verify; \
    else mvn -B -ntp verify; fi

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S banksync && adduser -S banksync -G banksync
WORKDIR /app
COPY --from=build --chown=banksync:banksync /build/target/banksync.jar app.jar
USER banksync
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
