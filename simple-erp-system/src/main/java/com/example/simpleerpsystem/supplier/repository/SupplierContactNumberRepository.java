package com.example.simpleerpsystem.supplier.repository;

import com.example.simpleerpsystem.supplier.entity.SupplierContactNumber;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SupplierContactNumberRepository extends ReactiveCrudRepository<SupplierContactNumber, Long> {
    Flux<SupplierContactNumber> findBySupplierId(Long supplierId);
}
