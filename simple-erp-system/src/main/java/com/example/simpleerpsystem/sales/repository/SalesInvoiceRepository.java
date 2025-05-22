package com.example.simpleerpsystem.sales.repository;

import com.example.simpleerpsystem.sales.entity.SalesInvoice;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalesInvoiceRepository extends ReactiveCrudRepository<SalesInvoice, Long> {
    // Custom query methods for filtering invoices can be added later
    // e.g., findByCustomerId, findByInvoiceDateBetween, findBySaleType
}
