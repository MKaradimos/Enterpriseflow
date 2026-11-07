# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies in their own layer so code changes don't re-download them.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI with Testcontainers; the image build only packages.
RUN mvn -B -q package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/enterpriseflow-backend-*.jar app.jar
USER app

EXPOSE 8080
# Respect container memory limits instead of the host's RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# 127.0.0.1 rather than localhost: in Alpine, localhost may resolve to ::1 first, which is not always listening.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
