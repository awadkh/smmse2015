package com.example.simpleerpsystem.customer.service;

import com.example.simpleerpsystem.customer.entity.Customer;
import com.example.simpleerpsystem.customer.entity.Document;
import com.example.simpleerpsystem.customer.entity.PhoneNumber;
import com.example.simpleerpsystem.customer.repository.CustomerRepository;
import com.example.simpleerpsystem.customer.repository.DocumentRepository;
import com.example.simpleerpsystem.customer.repository.PhoneNumberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PhoneNumberRepository phoneNumberRepository;
    private final DocumentRepository documentRepository;

    private Mono<Customer> populateCustomerDetails(Customer customer) {
        Mono<List<PhoneNumber>> phoneNumbersMono = phoneNumberRepository.findByCustomerId(customer.getId()).collectList();
        Mono<List<Document>> documentsMono = documentRepository.findByCustomerId(customer.getId()).collectList();

        return Mono.zip(phoneNumbersMono, documentsMono, (phones, docs) -> {
            customer.setPhoneNumbers(phones);
            customer.setDocuments(docs);
            return customer;
        });
    }

    public Flux<Customer> getAllCustomers() {
        return customerRepository.findAll()
                .flatMap(this::populateCustomerDetails);
    }

    public Mono<Customer> getCustomerById(Long id) {
        return customerRepository.findById(id)
                .flatMap(this::populateCustomerDetails);
    }

    public Mono<Customer> createCustomer(Customer customer) {
        // Create a new customer object to ensure no ID is carried over from the input
        final Customer newCustomer = new Customer(
                null, // id
                customer.getFullName(),
                customer.getNationalId(),
                customer.getAddressStreet(),
                customer.getAddressCity(),
                customer.getAddressRegion(),
                null, // phoneNumbers, will be populated after save
                null  // documents, will be populated after save
        );


        return customerRepository.save(newCustomer)
                .flatMap(savedC -> {
                    Long customerId = savedC.getId();

                    Mono<List<PhoneNumber>> phoneNumbersMono;
                    if (customer.getPhoneNumbers() != null && !customer.getPhoneNumbers().isEmpty()) {
                        phoneNumbersMono = Flux.fromIterable(customer.getPhoneNumbers())
                                .flatMap(phone -> {
                                    phone.setCustomerId(customerId);
                                    phone.setId(null); // Ensure new phone numbers are created
                                    return phoneNumberRepository.save(phone);
                                }).collectList();
                    } else {
                        phoneNumbersMono = Mono.just(Collections.emptyList());
                    }

                    Mono<List<Document>> documentsMono;
                    if (customer.getDocuments() != null && !customer.getDocuments().isEmpty()) {
                        documentsMono = Flux.fromIterable(customer.getDocuments())
                                .flatMap(doc -> {
                                    doc.setCustomerId(customerId);
                                    doc.setId(null); // Ensure new documents are created
                                    return documentRepository.save(doc);
                                }).collectList();
                    } else {
                        documentsMono = Mono.just(Collections.emptyList());
                    }

                    return Mono.zip(phoneNumbersMono, documentsMono, (phones, docs) -> {
                        savedC.setPhoneNumbers(phones);
                        savedC.setDocuments(docs);
                        return savedC;
                    });
                });
    }


    public Mono<Customer> updateCustomer(Long id, Customer customerDetails) {
        return customerRepository.findById(id)
                .flatMap(existingCustomer -> {
                    existingCustomer.setFullName(customerDetails.getFullName());
                    existingCustomer.setNationalId(customerDetails.getNationalId());
                    existingCustomer.setAddressStreet(customerDetails.getAddressStreet());
                    existingCustomer.setAddressCity(customerDetails.getAddressCity());
                    existingCustomer.setAddressRegion(customerDetails.getAddressRegion());
                    return customerRepository.save(existingCustomer);
                })
                .flatMap(this::populateCustomerDetails); // Populate after save
    }

    public Mono<Void> deleteCustomer(Long id) {
        return customerRepository.deleteById(id);
    }
}
