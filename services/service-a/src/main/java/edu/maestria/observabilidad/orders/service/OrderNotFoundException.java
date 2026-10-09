package edu.maestria.observabilidad.orders.service;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderId) {
        super("Pedido no encontrado: " + orderId);
    }
}
