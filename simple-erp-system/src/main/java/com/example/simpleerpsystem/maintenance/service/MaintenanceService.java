package com.example.simpleerpsystem.maintenance.service;

import com.example.simpleerpsystem.customer.repository.CustomerRepository;
import com.example.simpleerpsystem.maintenance.entity.ServiceRecord;
import com.example.simpleerpsystem.maintenance.entity.enums.ServiceType;
import com.example.simpleerpsystem.maintenance.repository.ServiceRecordRepository;
import com.example.simpleerpsystem.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final ServiceRecordRepository serviceRecordRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public Mono<ServiceRecord> createServiceRecord(ServiceRecord serviceRecordInput) {
        serviceRecordInput.setId(null); // Ensure creation

        // Default cost if null
        if (serviceRecordInput.getCost() == null) {
            serviceRecordInput.setCost(0.0);
        }
        // Default isFree based on cost if not explicitly set
        if (serviceRecordInput.getIsFree() == null) {
            serviceRecordInput.setIsFree(serviceRecordInput.getCost() == 0.0);
        }

        Mono<Boolean> customerExists = customerRepository.existsById(serviceRecordInput.getCustomerId());
        
        Mono<Boolean> productExistsMono;
        if (serviceRecordInput.getProductId() != null) {
            productExistsMono = productRepository.existsById(serviceRecordInput.getProductId());
        } else {
            productExistsMono = Mono.just(true); // If no product ID, effectively "exists" or not applicable
        }

        return customerExists.flatMap(customerExistsFlag -> {
            if (!customerExistsFlag) {
                return Mono.error(new RuntimeException("Customer not found with id: " + serviceRecordInput.getCustomerId()));
            }
            return productExistsMono;
        }).flatMap(productExistsFlag -> {
            if (!productExistsFlag) {
                // This will only be false if productId was not null and the product didn't exist
                return Mono.error(new RuntimeException("Product not found with id: " + serviceRecordInput.getProductId()));
            }
            return serviceRecordRepository.save(serviceRecordInput);
        });
    }

    public Mono<ServiceRecord> getServiceRecordById(Long id) {
        return serviceRecordRepository.findById(id);
    }

    public Flux<ServiceRecord> getAllServiceRecords() {
        return serviceRecordRepository.findAll();
    }

    public Mono<ServiceRecord> updateServiceRecord(Long id, ServiceRecord serviceRecordDetails) {
        return serviceRecordRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("ServiceRecord not found with id: " + id)))
            .flatMap(existingRecord -> {
                // Validate Customer if changed
                Mono<Boolean> customerValidationMono = Mono.just(true);
                if (serviceRecordDetails.getCustomerId() != null && !serviceRecordDetails.getCustomerId().equals(existingRecord.getCustomerId())) {
                    customerValidationMono = customerRepository.existsById(serviceRecordDetails.getCustomerId())
                        .flatMap(exists -> {
                            if (!exists) return Mono.error(new RuntimeException("New Customer not found with id: " + serviceRecordDetails.getCustomerId()));
                            return Mono.just(true);
                        });
                }

                // Validate Product if changed
                Mono<Boolean> productValidationMono = Mono.just(true);
                if (serviceRecordDetails.getProductId() != null && !serviceRecordDetails.getProductId().equals(existingRecord.getProductId())) {
                    productValidationMono = productRepository.existsById(serviceRecordDetails.getProductId())
                        .flatMap(exists -> {
                            if (!exists) return Mono.error(new RuntimeException("New Product not found with id: " + serviceRecordDetails.getProductId()));
                            return Mono.just(true);
                        });
                } else if (serviceRecordDetails.getProductId() == null && existingRecord.getProductId() != null) {
                    // Product is being removed, no validation needed for existence, but field should be updated
                    productValidationMono = Mono.just(true);
                }


                return customerValidationMono.then(productValidationMono)
                    .flatMap(validationResult -> {
                        existingRecord.setCustomerId(serviceRecordDetails.getCustomerId());
                        existingRecord.setProductId(serviceRecordDetails.getProductId()); // Can be null
                        existingRecord.setServiceType(serviceRecordDetails.getServiceType());
                        existingRecord.setDescription(serviceRecordDetails.getDescription());
                        existingRecord.setServiceDate(serviceRecordDetails.getServiceDate());
                        
                        if (serviceRecordDetails.getCost() != null) {
                            existingRecord.setCost(serviceRecordDetails.getCost());
                            // If cost is explicitly set, and isFree is not, recalculate isFree
                            if (serviceRecordDetails.getIsFree() == null) {
                                existingRecord.setIsFree(existingRecord.getCost() == 0.0);
                            } else {
                                existingRecord.setIsFree(serviceRecordDetails.getIsFree());
                            }
                        } else if (serviceRecordDetails.getIsFree() != null) {
                            // If only isFree is set, honor it. Cost remains unchanged or default if it was null.
                            existingRecord.setIsFree(serviceRecordDetails.getIsFree());
                            if (existingRecord.getIsFree() && existingRecord.getCost() > 0.0) {
                                // Optional: business rule, if set to free, should cost be zeroed?
                                // For now, we assume they can be independent if isFree is explicitly set.
                                // existingRecord.setCost(0.0); 
                            }
                        }
                        // If neither cost nor isFree is in details, they remain as is.

                        existingRecord.setNotes(serviceRecordDetails.getNotes());
                        return serviceRecordRepository.save(existingRecord);
                    });
            });
    }

    public Mono<Void> deleteServiceRecord(Long id) {
        return serviceRecordRepository.deleteById(id);
    }

    // Finder methods
    public Flux<ServiceRecord> getServiceRecordsByCustomerId(Long customerId) {
        return serviceRecordRepository.findByCustomerId(customerId);
    }

    public Flux<ServiceRecord> getServiceRecordsByProductId(Long productId) {
        return serviceRecordRepository.findByProductId(productId);
    }

    public Flux<ServiceRecord> getServiceRecordsByServiceType(ServiceType serviceType) {
        return serviceRecordRepository.findByServiceType(serviceType);
    }

    public Flux<ServiceRecord> getServiceRecordsByServiceDate(LocalDate serviceDate) {
        return serviceRecordRepository.findByServiceDate(serviceDate);
    }

    public Flux<ServiceRecord> getServiceRecordsByServiceDateBetween(LocalDate startDate, LocalDate endDate) {
        return serviceRecordRepository.findByServiceDateBetween(startDate, endDate);
    }

    public Flux<ServiceRecord> getServiceRecordsByCustomerIdAndServiceDateBetween(Long customerId, LocalDate startDate, LocalDate endDate) {
        return serviceRecordRepository.findByCustomerIdAndServiceDateBetween(customerId, startDate, endDate);
    }
}
