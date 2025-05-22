package com.example.simpleerpsystem.installment.entity;

import com.example.simpleerpsystem.sales.entity.enums.PaymentStatus; // Reusing from sales module
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Table("installment_payment")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstallmentPayment {

    @Id
    private Long id;

    @Column("installment_plan_id")
    private Long installmentPlanId;

    @Column("due_date")
    private LocalDate dueDate;

    @Column("payment_date")
    private LocalDate paymentDate; // Nullable, set when payment is made

    @Column("amount_due")
    private Double amountDue; // Typically the InstallmentPlan.installmentAmount

    @Column("amount_paid")
    private Double amountPaid; // Nullable

    @Column("payment_status")
    private PaymentStatus paymentStatus; // PENDING, PAID, PARTIAL, OVERDUE

    private String notes; // Optional, nullable
}
