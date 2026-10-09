#!/bin/sh
set -eu

JAVA_OPTS="${JAVA_OPTS:--XX:MaxRAMPercentage=75.0}"

if [ "${OTEL_ENABLED:-true}" = "false" ]; then
  echo "Arranque baseline: sin Java Agent de OpenTelemetry"
  exec java ${JAVA_OPTS} -jar /app/app.jar
fi

echo "Arranque instrumentado: Java Agent de OpenTelemetry"
exec java ${JAVA_OPTS} -javaagent:/otel/opentelemetry-javaagent.jar -jar /app/app.jar
