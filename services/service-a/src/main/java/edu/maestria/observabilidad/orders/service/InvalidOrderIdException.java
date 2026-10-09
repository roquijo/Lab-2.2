package edu.maestria.observabilidad.orders.service;

public class InvalidOrderIdException extends RuntimeException {

    public InvalidOrderIdException(String orderId) {
        super("Identificador de pedido inválido");
    }
}
