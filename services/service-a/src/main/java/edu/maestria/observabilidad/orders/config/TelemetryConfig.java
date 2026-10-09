package edu.maestria.observabilidad.orders.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Obtiene Tracer y Meter del API global. Con el Java Agent instalado, el agente
 * registra el SDK antes de main y estas llamadas devuelven los instrumentos reales.
 * Sin agente (pruebas o baseline) el API opera en modo no-op y la lógica de negocio
 * sigue igual. No se incluye el SDK en el artefacto de runtime para no duplicar
 * proveedores con el agente.
 */
@Configuration
public class TelemetryConfig {

    static final String INSTRUMENTATION_NAME = "edu.maestria.observabilidad.orders";
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
