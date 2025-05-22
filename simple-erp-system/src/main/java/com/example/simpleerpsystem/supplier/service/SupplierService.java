package com.example.simpleerpsystem.supplier.service;

import com.example.simpleerpsystem.supplier.entity.Supplier;
import com.example.simpleerpsystem.supplier.entity.SupplierContactNumber;
import com.example.simpleerpsystem.supplier.repository.SupplierContactNumberRepository;
import com.example.simpleerpsystem.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierContactNumberRepository supplierContactNumberRepository;

    public Mono<Supplier> createSupplier(Supplier supplierInput) {
        supplierInput.setId(null); // Ensure creation
        if (supplierInput.getAccountBalance() == null) {
            supplierInput.setAccountBalance(0.0);
        }

        // Validate uniqueness of name
        Mono<Supplier> checkName = supplierRepository.findByName(supplierInput.getName())
            .flatMap(existing -> Mono.error(new RuntimeException("Supplier name already exists: " + supplierInput.getName())));

        // Validate uniqueness of email if provided
        Mono<Supplier> checkEmail = Mono.empty(); // Default to no check needed
        if (supplierInput.getEmail() != null && !supplierInput.getEmail().isEmpty()) {
            checkEmail = supplierRepository.findByEmail(supplierInput.getEmail())
                .flatMap(existing -> Mono.error(new RuntimeException("Supplier email already exists: " + supplierInput.getEmail())));
        }

        // Chain validations then save
        return checkName.switchIfEmpty(Mono.defer(() -> checkEmail)) // If name is unique, check email. Use defer to ensure checkEmail is subscribed only if checkName is empty.
            .switchIfEmpty(Mono.defer(() -> { // If both unique (or email check not needed/passed), proceed to save
                Supplier supplierToSave = new Supplier(); // Create new instance
                supplierToSave.setName(supplierInput.getName());
                supplierToSave.setContactPerson(supplierInput.getContactPerson());
                supplierToSave.setEmail(supplierInput.getEmail());
                supplierToSave.setAddress(supplierInput.getAddress());
                supplierToSave.setSuppliedItemsDescription(supplierInput.getSuppliedItemsDescription());
                supplierToSave.setAccountBalance(supplierInput.getAccountBalance());
                // contactNumbers will be handled after save

                return supplierRepository.save(supplierToSave)
                    .flatMap(savedSupplier -> {
                        if (supplierInput.getContactNumbers() != null && !supplierInput.getContactNumbers().isEmpty()) {
                            return Flux.fromIterable(supplierInput.getContactNumbers())
                                .flatMap(cn -> {
                                    cn.setSupplierId(savedSupplier.getId());
                                    cn.setId(null); // Ensure creation of new contact numbers
                                    return supplierContactNumberRepository.save(cn);
                                })
                                .collectList()
                                .map(savedNumbers -> {
                                    savedSupplier.setContactNumbers(savedNumbers);
                                    return savedSupplier;
                                });
                        }
                        savedSupplier.setContactNumbers(Collections.emptyList());
                        return Mono.just(savedSupplier);
                    });
            }))
            .cast(Supplier.class); // Cast back to Supplier if needed (e.g. if checkEmail was the one that completed empty)
    }

    private Mono<Supplier> populateSupplierDetails(Supplier supplier) {
        if (supplier == null) {
            return Mono.empty();
        }
        return supplierContactNumberRepository.findBySupplierId(supplier.getId())
            .collectList()
            .map(contactNumbers -> {
                supplier.setContactNumbers(contactNumbers);
                return supplier;
            });
    }

    public Flux<Supplier> getAllSuppliers() {
        return supplierRepository.findAll()
                .flatMap(this::populateSupplierDetails);
    }

    public Mono<Supplier> getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .flatMap(this::populateSupplierDetails)
                .switchIfEmpty(Mono.error(new RuntimeException("Supplier not found with id: " + id)));
    }

    public Mono<Supplier> updateSupplier(Long id, Supplier supplierDetails) {
        return supplierRepository.findById(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Supplier not found with id: " + id)))
            .flatMap(existingSupplier -> {
                // Validate name uniqueness if changed
                Mono<Void> nameValidationMono = Mono.empty();
                if (supplierDetails.getName() != null && !supplierDetails.getName().equals(existingSupplier.getName())) {
                    nameValidationMono = supplierRepository.findByName(supplierDetails.getName())
                        .flatMap(found -> {
                            if (!found.getId().equals(existingSupplier.getId())) { // Another supplier has this name
                                return Mono.error(new RuntimeException("Supplier name already exists: " + supplierDetails.getName()));
                            }
                            return Mono.empty(); // Name is same as existing or belongs to this supplier
                        }).then();
                }

                // Validate email uniqueness if changed and provided
                Mono<Void> emailValidationMono = Mono.empty();
                if (supplierDetails.getEmail() != null && !supplierDetails.getEmail().isEmpty() && !supplierDetails.getEmail().equals(existingSupplier.getEmail())) {
                    emailValidationMono = supplierRepository.findByEmail(supplierDetails.getEmail())
                        .flatMap(found -> {
                             if (!found.getId().equals(existingSupplier.getId())) { // Another supplier has this email
                                return Mono.error(new RuntimeException("Supplier email already exists: " + supplierDetails.getEmail()));
                            }
                            return Mono.empty();
                        }).then();
                }
                
                return nameValidationMono.then(emailValidationMono)
                    .then(Mono.defer(() -> {
                        existingSupplier.setName(supplierDetails.getName() != null ? supplierDetails.getName() : existingSupplier.getName());
                        existingSupplier.setContactPerson(supplierDetails.getContactPerson() != null ? supplierDetails.getContactPerson() : existingSupplier.getContactPerson());
                        existingSupplier.setEmail(supplierDetails.getEmail()); // Can be set to null
                        existingSupplier.setAddress(supplierDetails.getAddress() != null ? supplierDetails.getAddress() : existingSupplier.getAddress());
                        existingSupplier.setSuppliedItemsDescription(supplierDetails.getSuppliedItemsDescription() != null ? supplierDetails.getSuppliedItemsDescription() : existingSupplier.getSuppliedItemsDescription());
                        existingSupplier.setAccountBalance(supplierDetails.getAccountBalance() != null ? supplierDetails.getAccountBalance() : existingSupplier.getAccountBalance());
                        
                        return supplierRepository.save(existingSupplier);
                    }))
                    .flatMap(updatedSupplier -> 
                        supplierContactNumberRepository.findBySupplierId(updatedSupplier.getId())
                            .collectList()
                            .flatMap(existingNumbers -> supplierContactNumberRepository.deleteAll(existingNumbers))
                            .then(Mono.defer(() -> {
                                if (supplierDetails.getContactNumbers() != null && !supplierDetails.getContactNumbers().isEmpty()) {
                                    return Flux.fromIterable(supplierDetails.getContactNumbers())
                                        .flatMap(cn -> {
                                            cn.setSupplierId(updatedSupplier.getId());
                                            cn.setId(null);
                                            return supplierContactNumberRepository.save(cn);
                                        })
                                        .collectList()
                                        .map(newNumbers -> {
                                            updatedSupplier.setContactNumbers(newNumbers);
                                            return updatedSupplier;
                                        });
                                }
                                updatedSupplier.setContactNumbers(Collections.emptyList());
                                return Mono.just(updatedSupplier);
                            }))
                    );
            });
    }

    public Mono<Void> deleteSupplier(Long id) {
        // Associated contact numbers will be deleted by CASCADE constraint in DB
        return supplierRepository.deleteById(id);
    }
}
