DROP TABLE IF EXISTS inventory;
CREATE TABLE inventory (
    product_sku VARCHAR(100) PRIMARY KEY,
    available INT NOT NULL,
    warehouse VARCHAR(50) NOT NULL,
    reserved INT NOT NULL
);
