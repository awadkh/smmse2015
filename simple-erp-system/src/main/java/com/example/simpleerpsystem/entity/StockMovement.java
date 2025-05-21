package com.example.simpleerpsystem.entity;

import com.example.simpleerpsystem.entity.enums.StockMovementReason;
import com.example.simpleerpsystem.entity.enums.StockMovementType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("stock_movement")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    @Id
    private Long id;

    @Column("product_id")
    private Long productId;

    @Column("quantity_change")
    private Integer quantityChange; // Positive for IN, negative for OUT

    @Column("movement_type")
    private StockMovementType type; // IN or OUT

    @Column("movement_reason")
    private StockMovementReason reason; // SALE, PURCHASE, ADJUSTMENT, etc.

    @Column("movement_date")
    private LocalDateTime movementDate;
}
