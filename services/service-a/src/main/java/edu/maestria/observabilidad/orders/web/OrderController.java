package edu.maestria.observabilidad.orders.web;

import edu.maestria.observabilidad.orders.client.InventoryClientException;
import edu.maestria.observabilidad.orders.domain.ErrorResponse;
import edu.maestria.observabilidad.orders.domain.OrderView;
import edu.maestria.observabilidad.orders.service.InvalidOrderIdException;
import edu.maestria.observabilidad.orders.service.OrderFulfillmentService;
import edu.maestria.observabilidad.orders.service.OrderNotFoundException;
import edu.maestria.observabilidad.orders.support.TraceIds;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
public class OrderController {

    private final OrderFulfillmentService fulfillment;

    public OrderController(OrderFulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderView compose(@PathVariable String orderId) {
        return fulfillment.compose(orderId);
    }
}

@RestControllerAdvice
class OrderExceptionHandler {

    @ExceptionHandler(InvalidOrderIdException.class)
    ResponseEntity<ErrorResponse> invalid(InvalidOrderIdException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("invalid_order_id", exception.getMessage(), TraceIds.current()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ErrorResponse> missing(OrderNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("order_not_found", exception.getMessage(), TraceIds.current()));
    }

    @ExceptionHandler(InventoryClientException.class)
    ResponseEntity<ErrorResponse> dependency(InventoryClientException exception) {
        HttpStatus status = exception.statusCode() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        String code = exception.statusCode() == 404 ? "inventory_not_found" : "inventory_unavailable";
        return ResponseEntity.status(status)
                .body(new ErrorResponse(code, exception.getMessage(), TraceIds.current()));
    }
}
