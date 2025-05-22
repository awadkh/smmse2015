package com.example.simpleerpsystem.sales.repository;

import com.example.simpleerpsystem.sales.entity.InvoiceItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface InvoiceItemRepository extends ReactiveCrudRepository<InvoiceItem, Long> {
    Flux<InvoiceItem> findBySalesInvoiceId(Long salesInvoiceId);
}
