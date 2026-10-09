package edu.maestria.observabilidad.orders.repository;

import edu.maestria.observabilidad.orders.domain.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Order> findById(String orderId) {
        List<Order> rows = jdbc.query(
                """
                SELECT id, product_sku, quantity, status, customer_id
                FROM orders
                WHERE id = ?
                """,
                (rs, rowNum) -> new Order(
                        rs.getString("id"),
                        rs.getString("product_sku"),
                        rs.getInt("quantity"),
                        rs.getString("status"),
                        rs.getString("customer_id")),
                orderId);
        return rows.stream().findFirst();
    }
}
