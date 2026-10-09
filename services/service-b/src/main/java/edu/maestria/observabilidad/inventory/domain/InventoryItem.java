package edu.maestria.observabilidad.inventory.domain;

public record InventoryItem(
        String productSku,
        int available,
        String warehouse,
        int reserved) {
}
