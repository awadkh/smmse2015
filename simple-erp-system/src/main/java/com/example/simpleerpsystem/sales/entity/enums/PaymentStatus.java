package com.example.simpleerpsystem.sales.entity.enums;

public enum PaymentStatus {
    PENDING_CASH,       // For cash sales not yet paid
    PAID,               // Fully paid (cash or final installment)
    PENDING_INSTALLMENT,// For installment sales, awaiting payments
    PARTIALLY_PAID,     // For installment sales, some payments made
    OVERDUE             // Payments are overdue
}
