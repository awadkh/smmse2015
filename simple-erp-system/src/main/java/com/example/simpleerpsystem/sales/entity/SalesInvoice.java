package com.example.simpleerpsystem.sales.entity;

import com.example.simpleerpsystem.sales.entity.enums.PaymentStatus;
import com.example.simpleerpsystem.sales.entity.enums.SaleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

@Table("sales_invoice")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesInvoice {

    @Id
    private Long id;

    @Column("customer_id")
    private Long customerId;

    @Column("invoice_date")
    private LocalDateTime invoiceDate;

    @Column("sale_type")
    private SaleType saleType;

    // Optional fields for discount
    @Column("discount_amount")
    private Double discountAmount; // Fixed amount discount

    @Column("discount_percentage")
    private Double discountPercentage; // Percentage discount (e.g., 0.1 for 10%)

    @Column("sub_total_amount") // Sum of all InvoiceItem subtotals
    private Double subTotalAmount;

    @Column("additional_services_total_amount") // Sum of all AdditionalServiceItem calculatedCosts
    private Double additionalServicesTotalAmount;

    @Column("total_amount") // (subTotalAmount - discount) + additionalServicesTotalAmount
    private Double totalAmount;

    @Column("payment_status")
    private PaymentStatus paymentStatus;

    @Transient
    private List<InvoiceItem> items;

    @Transient
    private List<AdditionalServiceItem> additionalServices;
}
