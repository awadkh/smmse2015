package com.example.simpleerpsystem.installment.entity.enums;

public enum InstallmentPlanStatus {
    ACTIVE,     // Plan is ongoing
    COMPLETED,  // All installments paid
    DEFAULTED,  // Customer has defaulted on payments (requires business logic to set)
    CANCELLED   // Plan was cancelled
}
