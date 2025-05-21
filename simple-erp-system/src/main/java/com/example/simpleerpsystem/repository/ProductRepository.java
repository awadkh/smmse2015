package com.example.simpleerpsystem.repository;

import com.example.simpleerpsystem.entity.Product;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends ReactiveCrudRepository<Product, Long> {
    // ReactiveCrudRepository already provides methods like findAll, findById, save, deleteById, etc.
    // Custom query methods can be added here if needed in the future.
}
