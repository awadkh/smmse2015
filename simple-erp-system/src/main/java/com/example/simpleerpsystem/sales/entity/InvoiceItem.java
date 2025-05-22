package com.example.simpleerpsystem.sales.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("sales_invoice_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder // Added builder for easier construction
public class InvoiceItem {

    @Id
    private Long id;

    @Column("sales_invoice_id")
    private Long salesInvoiceId;

    @Column("product_id")
    private Long productId;

    @Column("product_name")
    private String productName; // Denormalized for easy display on invoice, original name at time of sale

    private Integer quantity;

    @Column("unit_price")
    private Double unitPrice; // Price at time of sale

    private Double subtotal; // quantity * unitPrice
}
