package edu.maestria.observabilidad.inventory.repository;

import edu.maestria.observabilidad.inventory.domain.InventoryItem;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InventoryRepository {

    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<InventoryItem> findBySku(String sku) {
        List<InventoryItem> rows = jdbc.query(
                """
                SELECT product_sku, available, warehouse, reserved
                FROM inventory
                WHERE product_sku = ?
                """,
                (rs, rowNum) -> new InventoryItem(
                        rs.getString("product_sku"),
                        rs.getInt("available"),
                        rs.getString("warehouse"),
                        rs.getInt("reserved")),
                sku);
        return rows.stream().findFirst();
    }

    @Transactional
    public boolean reserve(String sku, int quantity) {
        int updated = jdbc.update(
                """
                UPDATE inventory
                SET available = available - ?, reserved = reserved + ?
                WHERE product_sku = ? AND available >= ?
                """,
                quantity, quantity, sku, quantity);
        return updated == 1;
    }
}
