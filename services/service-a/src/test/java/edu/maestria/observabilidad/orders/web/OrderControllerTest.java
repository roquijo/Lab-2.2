package edu.maestria.observabilidad.orders.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.maestria.observabilidad.orders.domain.InventoryStock;
import edu.maestria.observabilidad.orders.domain.OrderView;
import edu.maestria.observabilidad.orders.service.OrderFulfillmentService;
import edu.maestria.observabilidad.orders.service.OrderNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderFulfillmentService fulfillment;

    @Test
    void devuelveElPedidoCompuesto() throws Exception {
        when(fulfillment.compose("ord-001")).thenReturn(new OrderView(
                "ord-001",
                "LAPTOP-X1",
                1,
                "delivered",
                "cust-100",
                new InventoryStock("LAPTOP-X1", 15, "WH-BOGOTA", 2),
                true,
                "0123456789abcdef0123456789abcdef"));

        mvc.perform(get("/api/orders/ord-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ord-001"))
                .andExpect(jsonPath("$.inventory.available").value(15))
                .andExpect(jsonPath("$.stockSufficient").value(true))
                .andExpect(jsonPath("$.traceId").value("0123456789abcdef0123456789abcdef"));
    }

    @Test
    void responde404CuandoElPedidoNoExiste() throws Exception {
        when(fulfillment.compose("ord-404")).thenThrow(new OrderNotFoundException("ord-404"));

        mvc.perform(get("/api/orders/ord-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("order_not_found"));
    }
}
