package edu.maestria.observabilidad.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import edu.maestria.observabilidad.inventory.domain.InventoryItem;
import edu.maestria.observabilidad.inventory.domain.ReservationResult;
import edu.maestria.observabilidad.inventory.repository.InventoryRepository;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
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
class InventoryServiceTest {

    @Mock
    private InventoryRepository repository;

    private InMemorySpanExporter exporter;
    private Tracer tracer;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk telemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
        tracer = telemetry.getTracer("test");
        service = new InventoryService(repository, tracer, telemetry.getMeter("test"));
    }

    @Test
    void laConsultaQuedaEnLaMismaTrazaQueElSpanEntrante() {
        when(repository.findBySku("LAPTOP-X1")).thenReturn(
                Optional.of(new InventoryItem("LAPTOP-X1", 15, "WH-BOGOTA", 2)));

        Span parent = tracer.spanBuilder("GET /api/inventory/{sku}").startSpan();
        InventoryItem item;
        try (Scope ignored = parent.makeCurrent()) {
            item = service.lookup("LAPTOP-X1");
        } finally {
            parent.end();
        }

        assertThat(item.available()).isEqualTo(15);
        SpanData lookup = spanNamed("inventory.lookup");
        SpanData http = spanNamed("GET /api/inventory/{sku}");
        assertThat(lookup.getTraceId()).isEqualTo(http.getTraceId());
        assertThat(lookup.getParentSpanId()).isEqualTo(http.getSpanId());
        assertThat(lookup.getAttributes().get(AttributeKey.stringKey("inventory.warehouse"))).isEqualTo("WH-BOGOTA");
    }

    @Test
    void reservaConfirmadaActualizaElResultado() {
        when(repository.findBySku("LAPTOP-X1"))
                .thenReturn(Optional.of(new InventoryItem("LAPTOP-X1", 15, "WH-BOGOTA", 2)))
                .thenReturn(Optional.of(new InventoryItem("LAPTOP-X1", 14, "WH-BOGOTA", 3)));
        when(repository.reserve("LAPTOP-X1", 1)).thenReturn(true);

        ReservationResult result = service.reserve("LAPTOP-X1", 1);

        assertThat(result.status()).isEqualTo("confirmed");
        assertThat(result.available()).isEqualTo(14);
        assertThat(spanNamed("inventory.reserve").getAttributes().get(AttributeKey.booleanKey("reservation.approved")))
                .isTrue();
    }

    @Test
    void stockInsuficienteNoConfirmaLaReserva() {
        when(repository.findBySku("LAPTOP-X1")).thenReturn(
                Optional.of(new InventoryItem("LAPTOP-X1", 0, "WH-BOGOTA", 2)));
        when(repository.reserve("LAPTOP-X1", 1)).thenReturn(false);

        assertThatThrownBy(() -> service.reserve("LAPTOP-X1", 1)).isInstanceOf(InsufficientStockException.class);
        assertThat(spanNamed("inventory.reserve").getAttributes().get(AttributeKey.booleanKey("reservation.approved")))
                .isFalse();
    }

    private SpanData spanNamed(String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(span -> span.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
