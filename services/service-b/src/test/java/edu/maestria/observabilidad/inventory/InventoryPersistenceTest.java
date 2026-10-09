package edu.maestria.observabilidad.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/schema-test.sql", "/data-test.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class InventoryPersistenceTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void consultaElInventarioPersistido() throws Exception {
        mvc.perform(get("/api/inventory/LAPTOP-X1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productSku").value("LAPTOP-X1"))
                .andExpect(jsonPath("$.available").value(15))
                .andExpect(jsonPath("$.warehouse").value("WH-BOGOTA"));
    }

    @Test
    void reservaDescuentaUnidadesDisponibles() throws Exception {
        mvc.perform(post("/api/inventory/LAPTOP-X1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.available").value(14))
                .andExpect(jsonPath("$.reserved").value(3));

        mvc.perform(get("/api/inventory/LAPTOP-X1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(14));
    }

    @Test
    void productoDesconocidoResponde404() throws Exception {
        mvc.perform(get("/api/inventory/NO-EXISTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("product_not_found"));
    }
}
