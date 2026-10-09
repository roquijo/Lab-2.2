package edu.maestria.observabilidad.orders.domain;

public record Order(
        String id,
        String productSku,
        int quantity,
        String status,
        String customerId) {
}
