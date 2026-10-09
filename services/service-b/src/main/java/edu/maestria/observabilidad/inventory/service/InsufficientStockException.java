package edu.maestria.observabilidad.inventory.service;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku) {
        super("Stock insuficiente para " + sku);
    }
}
