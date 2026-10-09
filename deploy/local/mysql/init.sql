CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(36) PRIMARY KEY,
    product_sku VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    customer_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inventory (
    product_sku VARCHAR(100) PRIMARY KEY,
    available INT NOT NULL,
    warehouse VARCHAR(50) NOT NULL,
    reserved INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO orders (id, product_sku, quantity, status, customer_id) VALUES
    ('ord-001', 'LAPTOP-X1', 1, 'delivered', 'cust-100'),
    ('ord-002', 'MOUSE-PRO', 2, 'pending', 'cust-101'),
    ('ord-003', 'KEYBOARD-MK', 1, 'shipped', 'cust-102'),
    ('ord-004', 'MONITOR-4K', 1, 'pending', 'cust-103'),
    ('ord-005', 'HEADSET-Z', 1, 'cancelled', 'cust-104')
ON DUPLICATE KEY UPDATE product_sku = VALUES(product_sku);

INSERT INTO inventory (product_sku, available, warehouse, reserved) VALUES
    ('LAPTOP-X1', 15, 'WH-BOGOTA', 2),
    ('MOUSE-PRO', 50, 'WH-MAIN', 5),
    ('KEYBOARD-MK', 30, 'WH-MAIN', 0),
    ('MONITOR-4K', 8, 'WH-BOGOTA', 1),
    ('HEADSET-Z', 22, 'WH-MAIN', 0)
ON DUPLICATE KEY UPDATE available = VALUES(available);
