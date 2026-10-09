package edu.maestria.observabilidad.inventory.web;

import edu.maestria.observabilidad.inventory.domain.ErrorResponse;
import edu.maestria.observabilidad.inventory.domain.InventoryItem;
import edu.maestria.observabilidad.inventory.domain.ReservationRequest;
import edu.maestria.observabilidad.inventory.domain.ReservationResult;
import edu.maestria.observabilidad.inventory.service.InsufficientStockException;
import edu.maestria.observabilidad.inventory.service.InvalidReservationException;
import edu.maestria.observabilidad.inventory.service.InventoryService;
import edu.maestria.observabilidad.inventory.service.ProductNotFoundException;
import edu.maestria.observabilidad.inventory.support.TraceIds;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
public class InventoryController {

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/api/inventory/{sku}")
    public InventoryItem lookup(@PathVariable String sku) {
        return inventory.lookup(sku);
    }

    @PostMapping("/api/inventory/{sku}/reservations")
    public ReservationResult reserve(@PathVariable String sku, @RequestBody ReservationRequest request) {
        return inventory.reserve(sku, request.quantity());
    }
}

@RestControllerAdvice
class InventoryExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ErrorResponse> missing(ProductNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("product_not_found", exception.getMessage(), TraceIds.current()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    ResponseEntity<ErrorResponse> conflict(InsufficientStockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("insufficient_stock", exception.getMessage(), TraceIds.current()));
    }

    @ExceptionHandler(InvalidReservationException.class)
    ResponseEntity<ErrorResponse> invalid(InvalidReservationException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("invalid_quantity", exception.getMessage(), TraceIds.current()));
    }
}
