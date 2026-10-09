package edu.maestria.observabilidad.inventory.service;

import edu.maestria.observabilidad.inventory.domain.InventoryItem;
import edu.maestria.observabilidad.inventory.domain.ReservationResult;
import edu.maestria.observabilidad.inventory.repository.InventoryRepository;
import edu.maestria.observabilidad.inventory.support.TraceMdc;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final AttributeKey<String> RESULT = AttributeKey.stringKey("result");

    private final InventoryRepository repository;
    private final Tracer tracer;
    private final LongCounter lookups;
    private final LongCounter reservations;

    public InventoryService(InventoryRepository repository, Tracer tracer, Meter meter) {
        this.repository = repository;
        this.tracer = tracer;
        this.lookups = meter.counterBuilder("inventory.lookups")
                .setDescription("Consultas de inventario atendidas por service-b")
                .build();
        this.reservations = meter.counterBuilder("inventory.reservations")
                .setDescription("Intentos de reserva de inventario")
                .build();
    }

    public InventoryItem lookup(String sku) {
        Span span = tracer.spanBuilder("inventory.lookup")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        try (Scope ignored = span.makeCurrent(); TraceMdc ignoredMdc = new TraceMdc(span)) {
            span.setAttribute("product.sku", sku);
            InventoryItem item = repository.findBySku(sku).orElseThrow(() -> {
                span.setAttribute("inventory.found", false);
                lookups.add(1, Attributes.of(RESULT, "not_found"));
                log.info("Inventario no encontrado sku={}", sku);
                return new ProductNotFoundException(sku);
            });
            span.setAttribute("inventory.found", true);
            span.setAttribute("inventory.available", item.available());
            span.setAttribute("inventory.warehouse", item.warehouse());
            lookups.add(1, Attributes.of(RESULT, "hit"));
            log.info("Inventario consultado sku={} available={}", sku, item.available());
            return item;
        } catch (ProductNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            lookups.add(1, Attributes.of(RESULT, "error"));
            log.error("Error al consultar inventario sku={}", sku, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    public ReservationResult reserve(String sku, int quantity) {
        if (quantity <= 0) {
            throw new InvalidReservationException();
        }
        Span span = tracer.spanBuilder("inventory.reserve")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        try (Scope ignored = span.makeCurrent(); TraceMdc ignoredMdc = new TraceMdc(span)) {
            span.setAttribute("product.sku", sku);
            span.setAttribute("reservation.quantity", quantity);
            if (repository.findBySku(sku).isEmpty()) {
                span.setAttribute("inventory.found", false);
                reservations.add(1, Attributes.of(RESULT, "not_found"));
                log.info("Reserva rechazada, producto inexistente sku={}", sku);
                throw new ProductNotFoundException(sku);
            }
            boolean reserved = repository.reserve(sku, quantity);
            if (!reserved) {
                span.setAttribute("reservation.approved", false);
                reservations.add(1, Attributes.of(RESULT, "rejected"));
                log.info("Reserva rechazada por stock insuficiente sku={} quantity={}", sku, quantity);
                throw new InsufficientStockException(sku);
            }
            InventoryItem updated = repository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
            span.setAttribute("reservation.approved", true);
            span.setAttribute("inventory.available", updated.available());
            reservations.add(1, Attributes.of(RESULT, "confirmed"));
            log.info("Reserva confirmada sku={} quantity={} available={}", sku, quantity, updated.available());
            return new ReservationResult(sku, quantity, updated.available(), updated.reserved(), "confirmed");
        } catch (ProductNotFoundException | InsufficientStockException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            reservations.add(1, Attributes.of(RESULT, "error"));
            log.error("Error al reservar inventario sku={}", sku, exception);
            throw exception;
        } finally {
            span.end();
        }
    }
}
