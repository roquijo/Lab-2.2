package edu.maestria.observabilidad.inventory.domain;

public record ReservationResult(
        String productSku,
        int quantity,
        int available,
        int reserved,
        String status) {
}
