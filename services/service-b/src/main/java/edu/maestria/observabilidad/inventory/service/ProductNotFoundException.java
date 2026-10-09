package edu.maestria.observabilidad.inventory.service;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String sku) {
        super("Producto no encontrado: " + sku);
    }
}
