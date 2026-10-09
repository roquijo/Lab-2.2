package edu.maestria.observabilidad.orders.client;

import edu.maestria.observabilidad.orders.domain.InventoryStock;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(RestClient inventoryRestClient) {
        this.restClient = inventoryRestClient;
    }

    public InventoryStock fetch(String sku) {
        try {
            InventoryStock stock = restClient.get()
                    .uri("/api/inventory/{sku}", sku)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw new InventoryClientException(404, "Producto sin inventario");
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new InventoryClientException(
                                response.getStatusCode().value(),
                                "service-b respondió con error");
                    })
                    .body(InventoryStock.class);
            if (stock == null) {
                throw new InventoryClientException(502, "service-b devolvió un cuerpo vacío");
            }
            return stock;
        } catch (InventoryClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new InventoryClientException(502, "No fue posible contactar a service-b");
        }
    }
}
