FROM otel/opentelemetry-collector-contrib:0.162.0
COPY observability/collector/collector-aws.yaml /etc/otelcol/config.yaml
CMD ["--config=/etc/otelcol/config.yaml"]
