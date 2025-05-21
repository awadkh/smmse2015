package com.example.simpleerpsystem.customer.controller;

import com.example.simpleerpsystem.customer.entity.Customer;
import com.example.simpleerpsystem.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Flux<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Customer>> getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Customer> createCustomer(@RequestBody Customer customer) {
        // The customer object in the request body can include arrays for
        // phoneNumbers and documents. The CustomerService is designed to handle these.
        return customerService.createCustomer(customer);
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Customer>> updateCustomer(@PathVariable Long id, @RequestBody Customer customerDetails) {
        // Note: As per service design, this primarily updates direct fields of Customer.
        // phoneNumbers and documents in customerDetails might be ignored by the current
        // service update logic, but the response will contain the current state of these relations.
        return customerService.updateCustomer(id, customerDetails)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteCustomer(@PathVariable Long id) {
        return customerService.deleteCustomer(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
