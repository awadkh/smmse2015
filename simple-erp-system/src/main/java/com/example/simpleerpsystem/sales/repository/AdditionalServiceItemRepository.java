package com.example.simpleerpsystem.sales.repository;

import com.example.simpleerpsystem.sales.entity.AdditionalServiceItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface AdditionalServiceItemRepository extends ReactiveCrudRepository<AdditionalServiceItem, Long> {
    Flux<AdditionalServiceItem> findBySalesInvoiceId(Long salesInvoiceId);
}
