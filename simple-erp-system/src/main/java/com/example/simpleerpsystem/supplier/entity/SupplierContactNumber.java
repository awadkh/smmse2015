package com.example.simpleerpsystem.supplier.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("supplier_contact_number")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierContactNumber {

    @Id
    private Long id;

    @Column("supplier_id")
    private Long supplierId;

    @Column("phone_number")
    private String phoneNumber;
}
