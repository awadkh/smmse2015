package com.example.simpleerpsystem.repository;

import com.example.simpleerpsystem.entity.StockMovement;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface StockMovementRepository extends ReactiveCrudRepository<StockMovement, Long> {

    Flux<StockMovement> findByProductId(Long productId);

}
