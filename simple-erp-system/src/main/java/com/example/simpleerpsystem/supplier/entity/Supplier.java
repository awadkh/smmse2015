package com.example.simpleerpsystem.supplier.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.List;

@Table("supplier")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    private Long id;

    private String name; // Should be unique

    @Column("contact_person")
    private String contactPerson; // Optional

    private String email; // Optional, should be unique if provided

    private String address; // Optional

    @Column("supplied_items_description") // E.g., "Water Filters, AC Parts, Plumbing components"
    private String suppliedItemsDescription;

    @Column("account_balance") // Positive: we owe supplier. Negative: supplier owes us / prepayment.
    private Double accountBalance;

    @Transient
    private List<SupplierContactNumber> contactNumbers;
}
