package com.example.simpleerpsystem.installment.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record RecordPaymentRequest(
    @NotNull @Positive Double amountPaid,
    @NotNull LocalDate paymentDate
) {}
