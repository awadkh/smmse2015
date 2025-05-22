package com.example.simpleerpsystem.sales.entity;

import com.example.simpleerpsystem.sales.entity.enums.AdditionalServiceCostType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("sales_additional_service")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdditionalServiceItem {

    @Id
    private Long id;

    @Column("sales_invoice_id")
    private Long salesInvoiceId;

    @Column("service_name")
    private String serviceName; // e.g., "Installation", "Extended Warranty"

    @Column("cost_type")
    private AdditionalServiceCostType costType; // FIXED_AMOUNT or PERCENTAGE_OF_TOTAL

    // Value used for calculating the cost.
    // If costType is FIXED_AMOUNT, this is the amount.
    // If costType is PERCENTAGE_OF_TOTAL, this is the percentage (e.g., 0.06 for 6%).
    private Double value;

    @Column("calculated_cost")
    private Double calculatedCost; // The actual cost applied to the invoice
}
