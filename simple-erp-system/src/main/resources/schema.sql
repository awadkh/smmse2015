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

-- Sales related tables

DROP TABLE IF EXISTS sales_invoice_item;         -- Drop child table first
DROP TABLE IF EXISTS sales_additional_service;   -- Drop child table first
DROP TABLE IF EXISTS sales_invoice;              -- Drop parent table

CREATE TABLE sales_invoice (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    invoice_date TIMESTAMP NOT NULL,
    sale_type VARCHAR(50) NOT NULL,
    discount_amount DOUBLE PRECISION,          -- Nullable
    discount_percentage DOUBLE PRECISION,      -- Nullable
    sub_total_amount DOUBLE PRECISION NOT NULL,
    additional_services_total_amount DOUBLE PRECISION NOT NULL,
    total_amount DOUBLE PRECISION NOT NULL,
    payment_status VARCHAR(50) NOT NULL,
    FOREIGN KEY (customer_id) REFERENCES customer(id) -- Assuming customer cannot be deleted if they have invoices
);

-- Re-define sales_invoice_item with FOREIGN KEY to sales_invoice
CREATE TABLE sales_invoice_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sales_invoice_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DOUBLE PRECISION NOT NULL,
    subtotal DOUBLE PRECISION NOT NULL,
    FOREIGN KEY (product_id) REFERENCES product(id),
    FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoice(id) ON DELETE CASCADE -- If invoice is deleted, its items are deleted
);

-- Re-define sales_additional_service with FOREIGN KEY to sales_invoice
CREATE TABLE sales_additional_service (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sales_invoice_id BIGINT NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    cost_type VARCHAR(50) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    calculated_cost DOUBLE PRECISION NOT NULL,
    FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoice(id) ON DELETE CASCADE -- If invoice is deleted, its additional services are deleted
);

-- Installment related tables

DROP TABLE IF EXISTS installment_payment; -- Drop child table first if it exists (will be created next)
DROP TABLE IF EXISTS installment_plan;

CREATE TABLE installment_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sales_invoice_id BIGINT NOT NULL UNIQUE, -- Each sales invoice can have at most one installment plan
    total_installment_amount DOUBLE PRECISION NOT NULL,
    number_of_installments INT NOT NULL,
    installment_amount DOUBLE PRECISION NOT NULL,
    payment_interval VARCHAR(50) NOT NULL, -- Stores PaymentInterval enum name
    custom_interval_days INT, -- Nullable, for CUSTOM_DAYS interval
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(50) NOT NULL,          -- Stores InstallmentPlanStatus enum name
    FOREIGN KEY (sales_invoice_id) REFERENCES sales_invoice(id) ON DELETE RESTRICT -- Prevent deleting sales invoice if plan exists
                                                                                  -- or CASCADE if business rule allows plan deletion with invoice
);

DROP TABLE IF EXISTS installment_payment; -- Ensure it's dropped before creation if script is re-run

CREATE TABLE installment_payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    installment_plan_id BIGINT NOT NULL,
    due_date DATE NOT NULL,
    payment_date DATE, -- Nullable
    amount_due DOUBLE PRECISION NOT NULL,
    amount_paid DOUBLE PRECISION, -- Nullable
    payment_status VARCHAR(50) NOT NULL, -- Stores PaymentStatus enum name
    notes TEXT, -- Nullable
    FOREIGN KEY (installment_plan_id) REFERENCES installment_plan(id) ON DELETE CASCADE -- If plan is deleted, its payment schedule is deleted
);

-- Maintenance related tables

DROP TABLE IF EXISTS service_record;

CREATE TABLE service_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    product_id BIGINT, -- Nullable
    service_type VARCHAR(100) NOT NULL, -- Stores ServiceType enum name
    description TEXT,
    service_date DATE NOT NULL,
    cost DOUBLE PRECISION DEFAULT 0.0,
    is_free BOOLEAN DEFAULT FALSE,
    notes TEXT,
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE, -- If customer is deleted, their service records are deleted
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE SET NULL -- If product is deleted, keep service record but unlink product
);

-- Supplier related tables

DROP TABLE IF EXISTS supplier_contact_number;
DROP TABLE IF EXISTS supplier;

CREATE TABLE supplier (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    contact_person VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    address TEXT,
    supplied_items_description TEXT,
    account_balance DOUBLE PRECISION DEFAULT 0.0
);

CREATE TABLE supplier_contact_number (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    supplier_id BIGINT NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    FOREIGN KEY (supplier_id) REFERENCES supplier(id) ON DELETE CASCADE
);
