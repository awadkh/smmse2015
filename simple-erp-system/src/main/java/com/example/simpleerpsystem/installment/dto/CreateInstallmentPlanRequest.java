package com.example.simpleerpsystem.installment.dto;

import com.example.simpleerpsystem.installment.entity.enums.PaymentInterval;
import java.time.LocalDate;
import jakarta.validation.constraints.*; // For potential future validation

public record CreateInstallmentPlanRequest(
    @NotNull Long salesInvoiceId,
    @NotNull @Min(1) Integer numberOfInstallments,
    @NotNull PaymentInterval paymentInterval,
    Integer customIntervalDays, // Required if paymentInterval is CUSTOM_DAYS
    @NotNull LocalDate startDate
) {}
