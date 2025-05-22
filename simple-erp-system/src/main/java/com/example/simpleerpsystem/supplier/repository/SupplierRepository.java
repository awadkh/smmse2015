package com.example.simpleerpsystem.supplier.repository;

import com.example.simpleerpsystem.supplier.entity.Supplier;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface SupplierRepository extends ReactiveCrudRepository<Supplier, Long> {
    Mono<Supplier> findByName(String name); // Ensure supplier names are unique
    Mono<Supplier> findByEmail(String email); // Ensure emails are unique
}
