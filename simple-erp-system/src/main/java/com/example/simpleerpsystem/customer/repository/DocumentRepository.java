package com.example.simpleerpsystem.customer.repository;

import com.example.simpleerpsystem.customer.entity.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DocumentRepository extends ReactiveCrudRepository<Document, Long> {
    Flux<Document> findByCustomerId(Long customerId);
}
