package com.example.simpleerpsystem.customer.entity;

import com.example.simpleerpsystem.customer.entity.enums.PhoneNumberType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("customer_phone_number")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhoneNumber {

    @Id
    private Long id;

    private String number; // Consider adding validation annotations later if needed

    private PhoneNumberType type;

    @Column("customer_id")
    private Long customerId;
}
