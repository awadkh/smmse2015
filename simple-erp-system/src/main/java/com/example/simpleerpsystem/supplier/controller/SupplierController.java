package com.example.simpleerpsystem.supplier.controller;

import com.example.simpleerpsystem.supplier.entity.Supplier;
import com.example.simpleerpsystem.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Supplier> createSupplier(@RequestBody Supplier supplier) {
        return supplierService.createSupplier(supplier);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Supplier>> getSupplierById(@PathVariable Long id) {
        return supplierService.getSupplierById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<Supplier> getAllSuppliers() {
        return supplierService.getAllSuppliers();
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Supplier>> updateSupplier(@PathVariable Long id, @RequestBody Supplier supplierDetails) {
        return supplierService.updateSupplier(id, supplierDetails)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteSupplier(@PathVariable Long id) {
        return supplierService.deleteSupplier(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
