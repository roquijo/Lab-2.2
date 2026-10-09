package edu.maestria.observabilidad.inventory.service;

public class InvalidReservationException extends RuntimeException {

    public InvalidReservationException() {
        super("La cantidad a reservar debe ser mayor que cero");
    }
}
