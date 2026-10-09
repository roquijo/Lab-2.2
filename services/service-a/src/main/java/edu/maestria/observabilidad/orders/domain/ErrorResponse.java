package edu.maestria.observabilidad.orders.domain;

public record ErrorResponse(String error, String detail, String traceId) {
}
