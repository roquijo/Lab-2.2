package edu.maestria.observabilidad.orders.domain;

public record OrderView(
        String orderId,
        String productSku,
        int quantity,
        String status,
        String customerId,
        InventoryStock inventory,
        boolean stockSufficient,
        String traceId) {
}
