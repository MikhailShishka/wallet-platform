# ============================================================
# api-gateway Dockerfile — multi-stage build
# ============================================================

# --------- Stage 1: BUILDER ---------
FROM maven:3.9-eclipse-temurin-26 AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# --------- Stage 2: RUNTIME ---------
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8000/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75", \
    "-XX:+UseContainerSupport", \
    "-jar", "app.jar"]
