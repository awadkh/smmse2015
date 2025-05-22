package com.example.simpleerpsystem.sales.controller;

import com.example.simpleerpsystem.sales.entity.SalesInvoice;
import com.example.simpleerpsystem.sales.service.SalesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/sales-invoices")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    @GetMapping
    public Flux<SalesInvoice> getAllSalesInvoices() {
        return salesService.getAllSalesInvoices();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<SalesInvoice>> getSalesInvoiceById(@PathVariable Long id) {
        return salesService.getSalesInvoiceById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SalesInvoice> createSalesInvoice(@RequestBody SalesInvoice salesInvoice) {
        return salesService.createSalesInvoice(salesInvoice);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteSalesInvoice(@PathVariable Long id) {
        return salesService.deleteSalesInvoice(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
