package com.example.simpleerpsystem.entity;

import com.example.simpleerpsystem.entity.enums.ProductCategory; // New import
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("product")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    private Long id;

    private String name;
    private String description;
    private Double price;

    @Column("quantity_on_hand")
    private Integer quantityOnHand;

    // New fields
    private ProductCategory category;

    @Column("low_stock_threshold")
    private Integer lowStockThreshold;
}
