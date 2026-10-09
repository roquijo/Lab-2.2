package edu.maestria.observabilidad.orders.service;

import edu.maestria.observabilidad.orders.client.InventoryClient;
import edu.maestria.observabilidad.orders.client.InventoryClientException;
import edu.maestria.observabilidad.orders.domain.InventoryStock;
import edu.maestria.observabilidad.orders.domain.Order;
import edu.maestria.observabilidad.orders.domain.OrderView;
import edu.maestria.observabilidad.orders.repository.OrderRepository;
import edu.maestria.observabilidad.orders.support.TraceIds;
import edu.maestria.observabilidad.orders.support.TraceMdc;
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
public class OrderFulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(OrderFulfillmentService.class);
    private static final AttributeKey<String> RESULT = AttributeKey.stringKey("result");

    private final OrderRepository orders;
    private final InventoryClient inventoryClient;
    private final Tracer tracer;
    private final LongCounter ordersComposed;

    public OrderFulfillmentService(OrderRepository orders, InventoryClient inventoryClient, Tracer tracer, Meter meter) {
        this.orders = orders;
        this.inventoryClient = inventoryClient;
        this.tracer = tracer;
        this.ordersComposed = meter.counterBuilder("orders.composed")
                .setDescription("Pedidos compuestos por service-a, clasificados por resultado")
                .build();
    }

    public OrderView compose(String orderId) {
        if (orderId == null || !orderId.matches("[A-Za-z0-9_-]{1,36}")) {
            throw new InvalidOrderIdException(orderId);
        }

        Span span = tracer.spanBuilder("order.compose")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        try (Scope ignored = span.makeCurrent(); TraceMdc ignoredMdc = new TraceMdc(span)) {
            span.setAttribute("order.id", orderId);
            Order order = orders.findById(orderId).orElseThrow(() -> {
                span.setAttribute("order.found", false);
                ordersComposed.add(1, Attributes.of(RESULT, "not_found"));
                log.info("Pedido no encontrado orderId={}", orderId);
                return new OrderNotFoundException(orderId);
            });

            span.setAttribute("order.found", true);
            span.setAttribute("product.sku", order.productSku());
            span.setAttribute("order.status", order.status());

            InventoryStock stock = inventoryClient.fetch(order.productSku());
            boolean sufficient = stock.available() >= order.quantity();
            span.setAttribute("inventory.available", stock.available());
            span.setAttribute("inventory.warehouse", stock.warehouse());
            span.setAttribute("order.stock_sufficient", sufficient);
            ordersComposed.add(1, Attributes.of(RESULT, "success"));
            log.info(
                    "Pedido compuesto orderId={} sku={} sufficient={}",
                    order.id(),
                    order.productSku(),
                    sufficient);

            return new OrderView(
                    order.id(),
                    order.productSku(),
                    order.quantity(),
                    order.status(),
                    order.customerId(),
                    stock,
                    sufficient,
                    TraceIds.current());
        } catch (OrderNotFoundException exception) {
            throw exception;
        } catch (InventoryClientException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            span.setAttribute("http.response.status_code", exception.statusCode());
            ordersComposed.add(1, Attributes.of(RESULT, "dependency_error"));
            log.error("Fallo al consultar inventario orderId={} status={}", orderId, exception.statusCode());
            throw exception;
        } catch (RuntimeException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, exception.getMessage());
            ordersComposed.add(1, Attributes.of(RESULT, "error"));
            log.error("Error al componer el pedido orderId={}", orderId, exception);
            throw exception;
        } finally {
            span.end();
        }
    }
}
