package com.example.simpleerpsystem.maintenance.repository;

import com.example.simpleerpsystem.maintenance.entity.ServiceRecord;
import com.example.simpleerpsystem.maintenance.entity.enums.ServiceType;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import java.time.LocalDate;

@Repository
public interface ServiceRecordRepository extends ReactiveCrudRepository<ServiceRecord, Long> {

    Flux<ServiceRecord> findByCustomerId(Long customerId);

    Flux<ServiceRecord> findByProductId(Long productId); // Useful for product specific service history

    Flux<ServiceRecord> findByServiceType(ServiceType serviceType);

    Flux<ServiceRecord> findByServiceDate(LocalDate serviceDate);

    Flux<ServiceRecord> findByServiceDateBetween(LocalDate startDate, LocalDate endDate);

    Flux<ServiceRecord> findByCustomerIdAndServiceDateBetween(Long customerId, LocalDate startDate, LocalDate endDate);

    // Consider adding more complex filtering methods if needed, e.g., combining multiple criteria
    // For example: findByCustomerIdAndServiceTypeAndServiceDateBetween(...)
}
