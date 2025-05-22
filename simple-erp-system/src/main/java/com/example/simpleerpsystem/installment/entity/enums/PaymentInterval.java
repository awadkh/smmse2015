package com.example.simpleerpsystem.installment.entity.enums;

public enum PaymentInterval {
    MONTHLY,
    QUARTERLY,
    ANNUALLY,
    CUSTOM_DAYS // For more flexibility if needed later, implies a 'customDaysValue' field in InstallmentPlan
}
