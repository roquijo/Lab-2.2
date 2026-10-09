package edu.maestria.observabilidad.orders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.maestria.observabilidad.orders.client.InventoryClient;
import edu.maestria.observabilidad.orders.domain.InventoryStock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InventoryClient inventoryClient;

    @Test
    void leeElPedidoEnLaBaseYConsultaInventario() throws Exception {
        when(inventoryClient.fetch("LAPTOP-X1")).thenReturn(
                new InventoryStock("LAPTOP-X1", 15, "WH-BOGOTA", 2));

        mvc.perform(get("/api/orders/ord-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productSku").value("LAPTOP-X1"))
                .andExpect(jsonPath("$.customerId").value("cust-100"))
                .andExpect(jsonPath("$.inventory.warehouse").value("WH-BOGOTA"))
                .andExpect(jsonPath("$.stockSufficient").value(true));
    }

    @Test
    void pedidoAusenteResponde404SinLlamarInventario() throws Exception {
        mvc.perform(get("/api/orders/ord-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("order_not_found"));
    }
}
