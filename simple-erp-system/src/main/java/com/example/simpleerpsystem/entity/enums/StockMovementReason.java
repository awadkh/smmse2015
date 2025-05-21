package com.example.simpleerpsystem.entity.enums;

public enum StockMovementReason {
    SALE,
    PURCHASE,
    ADJUSTMENT_IN, // Manual positive adjustment
    ADJUSTMENT_OUT, // Manual negative adjustment (e.g. damaged goods)
    RETURN_CUSTOMER, // Customer returned product
    RETURN_SUPPLIER // Product returned to supplier
}
