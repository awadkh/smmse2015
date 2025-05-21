package com.example.simpleerpsystem.customer.repository;

import com.example.simpleerpsystem.customer.entity.Customer;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends ReactiveCrudRepository<Customer, Long> {
    // Custom query methods can be added here if needed for filtering
    // e.g., Flux<Customer> findByFullNameContainingIgnoreCase(String name);
    // Flux<Customer> findByNationalId(String nationalId);
}
