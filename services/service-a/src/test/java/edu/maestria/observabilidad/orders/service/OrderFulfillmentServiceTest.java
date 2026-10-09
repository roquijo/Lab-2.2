package edu.maestria.observabilidad.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.maestria.observabilidad.orders.client.InventoryClient;
import edu.maestria.observabilidad.orders.client.InventoryClientException;
import edu.maestria.observabilidad.orders.domain.InventoryStock;
import edu.maestria.observabilidad.orders.domain.Order;
import edu.maestria.observabilidad.orders.domain.OrderView;
import edu.maestria.observabilidad.orders.repository.OrderRepository;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderFulfillmentServiceTest {

    @Mock
    private OrderRepository orders;

    @Mock
    private InventoryClient inventoryClient;

    private InMemorySpanExporter exporter;
    private Tracer tracer;
    private OrderFulfillmentService service;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk telemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        tracer = telemetry.getTracer("test");
        service = new OrderFulfillmentService(orders, inventoryClient, tracer, telemetry.getMeter("test"));
    }

    @Test
    void componePedidoYCuelgaElSpanDeNegocioDelSpanPadre() {
        when(orders.findById("ord-001")).thenReturn(Optional.of(
                new Order("ord-001", "LAPTOP-X1", 1, "delivered", "cust-100")));
        when(inventoryClient.fetch("LAPTOP-X1")).thenReturn(
                new InventoryStock("LAPTOP-X1", 15, "WH-BOGOTA", 2));

        Span parent = tracer.spanBuilder("GET /api/orders/{orderId}").startSpan();
        OrderView view;
        try (Scope ignored = parent.makeCurrent()) {
            view = service.compose("ord-001");
        } finally {
            parent.end();
        }

        assertThat(view.orderId()).isEqualTo("ord-001");
        assertThat(view.stockSufficient()).isTrue();
        assertThat(view.traceId()).isEqualTo(parent.getSpanContext().getTraceId());
        verify(inventoryClient).fetch("LAPTOP-X1");

        SpanData business = spanNamed("order.compose");
        SpanData http = spanNamed("GET /api/orders/{orderId}");
        assertThat(business.getTraceId()).isEqualTo(http.getTraceId());
        assertThat(business.getParentSpanId()).isEqualTo(http.getSpanId());
        assertThat(business.getAttributes().get(AttributeKey.stringKey("product.sku"))).isEqualTo("LAPTOP-X1");
        assertThat(business.getAttributes().get(AttributeKey.booleanKey("order.stock_sufficient"))).isTrue();
    }

    @Test
    void pedidoInexistenteNoMarcaElSpanComoErrorDeServidor() {
        when(orders.findById("ord-404")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compose("ord-404")).isInstanceOf(OrderNotFoundException.class);

        SpanData business = spanNamed("order.compose");
        assertThat(business.getAttributes().get(AttributeKey.booleanKey("order.found"))).isFalse();
        assertThat(business.getStatus().getStatusCode()).isNotEqualTo(StatusCode.ERROR);
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void falloDeServiceBMarcaElSpanDeNegocio() {
        when(orders.findById("ord-001")).thenReturn(Optional.of(
                new Order("ord-001", "LAPTOP-X1", 1, "delivered", "cust-100")));
        when(inventoryClient.fetch("LAPTOP-X1")).thenThrow(new InventoryClientException(502, "caido"));

        assertThatThrownBy(() -> service.compose("ord-001")).isInstanceOf(InventoryClientException.class);

        SpanData business = spanNamed("order.compose");
        assertThat(business.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(business.getEvents()).isNotEmpty();
    }

    @Test
    void identificadorInvalidoNoAbreSpanDeNegocio() {
        assertThatThrownBy(() -> service.compose("pedido con espacios")).isInstanceOf(InvalidOrderIdException.class);
        assertThat(exporter.getFinishedSpanItems()).isEmpty();
        verifyNoInteractions(orders, inventoryClient);
    }

    private SpanData spanNamed(String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(span -> span.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
