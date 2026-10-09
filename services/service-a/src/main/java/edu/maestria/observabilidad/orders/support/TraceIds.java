package edu.maestria.observabilidad.orders.support;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

public final class TraceIds {

    private TraceIds() {
    }

    public static String current() {
        SpanContext context = Span.current().getSpanContext();
        if (!context.isValid()) {
            return null;
        }
        return context.getTraceId();
    }
}
