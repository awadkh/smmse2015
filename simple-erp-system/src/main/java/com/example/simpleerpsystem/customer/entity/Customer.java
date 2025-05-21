package com.example.simpleerpsystem.customer.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.List;

@Table("customer")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    private Long id;

    @Column("full_name")
    private String fullName;

    @Column("national_id")
    private String nationalId;

    @Column("address_street")
    private String addressStreet;

    @Column("address_city")
    private String addressCity;

    @Column("address_region")
    private String addressRegion;

    @Transient
    private List<PhoneNumber> phoneNumbers;

    @Transient
    private List<Document> documents;
}
