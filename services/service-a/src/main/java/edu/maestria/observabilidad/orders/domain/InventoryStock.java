package edu.maestria.observabilidad.orders.domain;

public record InventoryStock(
        String productSku,
        int available,
        String warehouse,
        int reserved) {
}
