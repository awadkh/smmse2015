package com.example.simpleerpsystem.installment.entity;

import com.example.simpleerpsystem.installment.entity.enums.InstallmentPlanStatus;
import com.example.simpleerpsystem.installment.entity.enums.PaymentInterval;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.List; // New import
import org.springframework.data.annotation.Transient; // New import

@Table("installment_plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentPlan {

    @Id
    private Long id;

    @Column("sales_invoice_id")
    private Long salesInvoiceId; // Should be unique

    @Column("total_installment_amount")
    private Double totalInstallmentAmount; // Usually the totalAmount from SalesInvoice

    @Column("number_of_installments")
    private Integer numberOfInstallments;

    @Column("installment_amount") // totalInstallmentAmount / numberOfInstallments (can be slightly adjusted for rounding)
    private Double installmentAmount;

    @Column("payment_interval")
    private PaymentInterval paymentInterval;

    @Column("custom_interval_days") // Used if paymentInterval is CUSTOM_DAYS
    private Integer customIntervalDays; // Nullable

    @Column("start_date")
    private LocalDate startDate;

    @Column("end_date") // Calculated: startDate + (numberOfInstallments * interval)
    private LocalDate endDate;

    private InstallmentPlanStatus status;

    @Transient
    private List<InstallmentPayment> payments;
}
