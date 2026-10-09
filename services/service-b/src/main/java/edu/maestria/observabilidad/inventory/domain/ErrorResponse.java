package edu.maestria.observabilidad.inventory.domain;

public record ErrorResponse(String error, String detail, String traceId) {
}
