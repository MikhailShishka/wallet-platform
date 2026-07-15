#!/bin/bash
# ============================================================
# run-with-otel.sh
# Запускает Spring Boot приложение с OpenTelemetry auto-instrumentation.
#
# Использование:
#   ./run-with-otel.sh wallet-service 8080
#
# Требования:
# 1. Скачан OTel Java agent (см. README):
#    curl -L -o opentelemetry-javaagent.jar \
#      https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar
# 2. Запущен docker-compose (для доступа к Jaeger на localhost:4318).
# ============================================================

set -e

SERVICE=${1:?"Usage: ./run-with-otel.sh <service-dir> [port]"}
PORT=${2:-8080}
AGENT="$(pwd)/opentelemetry-javaagent.jar"

if [ ! -f "$AGENT" ]; then
    echo "OTel agent not found at $AGENT"
    echo "Download it:"
    echo "  curl -L -o opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar"
    exit 1
fi

cd "$SERVICE"

# Ключевые переменные окружения OTel javaagent:
#
# OTEL_SERVICE_NAME — имя сервиса в Jaeger UI.
# OTEL_EXPORTER_OTLP_ENDPOINT — куда слать telemetry (Jaeger).
# OTEL_EXPORTER_OTLP_PROTOCOL — HTTP proto для лёгкой отладки (можно и grpc).
# OTEL_TRACES_SAMPLER — сколько запросов трейсить. always_on = все (для dev).
# OTEL_METRICS_EXPORTER=none — метрики оставляем через Micrometer/Prometheus.
# OTEL_LOGS_EXPORTER=none — логи оставляем в stdout / MDC (не отправляем через OTLP).

export OTEL_SERVICE_NAME="$SERVICE"
export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4318"
export OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf"
export OTEL_TRACES_SAMPLER="always_on"
export OTEL_METRICS_EXPORTER="none"
export OTEL_LOGS_EXPORTER="none"

# Инжектим trace_id / span_id в MDC → в логи Logback
export OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED="true"

# Ключевой аргумент — -javaagent прикрепляет OTel к JVM
export MAVEN_OPTS="-javaagent:$AGENT"

echo "Starting $SERVICE with OpenTelemetry agent"
echo "  Jaeger UI: http://localhost:16686"
echo "  Service: $SERVICE on port $PORT"
echo ""

./mvnw spring-boot:run
