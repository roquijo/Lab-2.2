package edu.maestria.observabilidad.inventory.support;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.MDC;

public final class TraceMdc implements AutoCloseable {

    private final boolean injected;

    public TraceMdc(Span span) {
        SpanContext context = span.getSpanContext();
        if (context.isValid() && MDC.get("trace_id") == null) {
            MDC.put("trace_id", context.getTraceId());
            MDC.put("span_id", context.getSpanId());
            injected = true;
        } else {
            injected = false;
        }
    }

    @Override
    public void close() {
        if (injected) {
            MDC.remove("trace_id");
            MDC.remove("span_id");
        }
    }
}
