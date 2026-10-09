package edu.maestria.observabilidad.inventory.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelemetryConfig {

    static final String INSTRUMENTATION_NAME = "edu.maestria.observabilidad.inventory";
    static final String INSTRUMENTATION_VERSION = "1.0.0";

    @Bean
    OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }

    @Bean
    Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(INSTRUMENTATION_NAME, INSTRUMENTATION_VERSION);
    }

    @Bean
    Meter meter(OpenTelemetry openTelemetry) {
        return openTelemetry.meterBuilder(INSTRUMENTATION_NAME)
                .setInstrumentationVersion(INSTRUMENTATION_VERSION)
                .build();
    }
}
