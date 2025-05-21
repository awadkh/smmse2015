package com.example.simpleerpsystem.customer.repository;

import com.example.simpleerpsystem.customer.entity.PhoneNumber;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface PhoneNumberRepository extends ReactiveCrudRepository<PhoneNumber, Long> {
    Flux<PhoneNumber> findByCustomerId(Long customerId);
}
