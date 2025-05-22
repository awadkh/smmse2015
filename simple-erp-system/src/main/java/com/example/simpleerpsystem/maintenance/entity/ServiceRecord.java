package com.example.simpleerpsystem.maintenance.entity;

import com.example.simpleerpsystem.maintenance.entity.enums.ServiceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Table("service_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRecord {

    @Id
    private Long id;

    @Column("customer_id")
    private Long customerId;

    @Column("product_id") // Nullable, if the service is tied to a specific product instance
    private Long productId;

    @Column("service_type")
    private ServiceType serviceType;

    private String description; // Detailed description of the service performed

    @Column("service_date")
    private LocalDate serviceDate;

    private Double cost; // Cost of the service. Can be 0.00

    @Column("is_free") // Can be explicitly set, or derived (e.g., if cost is 0.00)
    private Boolean isFree;

    private String notes; // Optional internal notes
}
