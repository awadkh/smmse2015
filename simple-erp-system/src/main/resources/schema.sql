DROP TABLE IF EXISTS product; -- Add this to ensure clean recreation for init.mode=always

CREATE TABLE product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DOUBLE PRECISION NOT NULL,
    quantity_on_hand INT NOT NULL,
    category VARCHAR(50), -- New column
    low_stock_threshold INT DEFAULT 0 -- New column
);

DROP TABLE IF EXISTS stock_movement;

CREATE TABLE stock_movement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    quantity_change INT NOT NULL,
    movement_type VARCHAR(50) NOT NULL, -- Will store enum name
    movement_reason VARCHAR(50) NOT NULL, -- Will store enum name
    movement_date TIMESTAMP NOT NULL,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE -- Added FK
);

-- Customer related tables

DROP TABLE IF EXISTS customer_phone_number; -- Drop child table first
DROP TABLE IF EXISTS customer_document;   -- Drop child table first
DROP TABLE IF EXISTS customer;            -- Drop parent table

CREATE TABLE customer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(255) NOT NULL,
    national_id VARCHAR(50) UNIQUE, -- Added UNIQUE constraint
    address_street VARCHAR(255),
    address_city VARCHAR(100),
    address_region VARCHAR(100)
);

-- Re-define customer_phone_number with FOREIGN KEY
CREATE TABLE customer_phone_number (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    number VARCHAR(20) NOT NULL,
    type VARCHAR(50) NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE
);

-- Re-define customer_document with FOREIGN KEY
CREATE TABLE customer_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    file_path_or_data VARCHAR(1024) NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE
);
