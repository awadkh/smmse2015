package com.example.simpleerpsystem.maintenance.controller;

import com.example.simpleerpsystem.maintenance.entity.ServiceRecord;
import com.example.simpleerpsystem.maintenance.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/maintenance/service-records")
@RequiredArgsConstructor
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ServiceRecord> createServiceRecord(@RequestBody ServiceRecord serviceRecord) {
        return maintenanceService.createServiceRecord(serviceRecord);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ServiceRecord>> getServiceRecordById(@PathVariable Long id) {
        return maintenanceService.getServiceRecordById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<ServiceRecord> getAllServiceRecords() {
        return maintenanceService.getAllServiceRecords();
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ServiceRecord>> updateServiceRecord(@PathVariable Long id, @RequestBody ServiceRecord serviceRecordDetails) {
        return maintenanceService.updateServiceRecord(id, serviceRecordDetails)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteServiceRecord(@PathVariable Long id) {
        return maintenanceService.deleteServiceRecord(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // Filtering Endpoints
    @GetMapping("/customer/{customerId}")
    public Flux<ServiceRecord> getServiceRecordsByCustomerId(@PathVariable Long customerId) {
        return maintenanceService.getServiceRecordsByCustomerId(customerId);
    }

    @GetMapping("/product/{productId}")
    public Flux<ServiceRecord> getServiceRecordsByProductId(@PathVariable Long productId) {
        return maintenanceService.getServiceRecordsByProductId(productId);
    }

    @GetMapping("/date-range")
    public Flux<ServiceRecord> getServiceRecordsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return maintenanceService.getServiceRecordsByServiceDateBetween(startDate, endDate);
    }
}
