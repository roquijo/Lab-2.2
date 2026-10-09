package edu.maestria.observabilidad.orders.client;

public class InventoryClientException extends RuntimeException {

    private final int statusCode;

    public InventoryClientException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
