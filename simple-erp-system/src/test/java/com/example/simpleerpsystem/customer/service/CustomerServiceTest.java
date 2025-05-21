package com.example.simpleerpsystem.customer.service;

import com.example.simpleerpsystem.customer.entity.Customer;
import com.example.simpleerpsystem.customer.entity.Document;
import com.example.simpleerpsystem.customer.entity.PhoneNumber;
import com.example.simpleerpsystem.customer.entity.enums.DocumentType;
import com.example.simpleerpsystem.customer.entity.enums.PhoneNumberType;
import com.example.simpleerpsystem.customer.repository.CustomerRepository;
import com.example.simpleerpsystem.customer.repository.DocumentRepository;
import com.example.simpleerpsystem.customer.repository.PhoneNumberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private PhoneNumberRepository phoneNumberRepository;

    @Mock
    private DocumentRepository documentRepository;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, phoneNumberRepository, documentRepository);
    }

    @Test
    void testGetAllCustomers() {
        Customer customer1 = new Customer(1L, "John Doe", "12345", "Street 1", "City A", "Region X", null, null);
        Customer customer2 = new Customer(2L, "Jane Smith", "67890", "Street 2", "City B", "Region Y", null, null);

        PhoneNumber phone1 = new PhoneNumber(101L, "555-0101", PhoneNumberType.PRIMARY, 1L);
        Document doc1 = new Document(201L, DocumentType.NATIONAL_ID_COPY, "/path/to/id1.jpg", 1L);
        PhoneNumber phone2 = new PhoneNumber(102L, "555-0102", PhoneNumberType.PRIMARY, 2L);

        when(customerRepository.findAll()).thenReturn(Flux.just(customer1, customer2));
        when(phoneNumberRepository.findByCustomerId(1L)).thenReturn(Flux.just(phone1));
        when(documentRepository.findByCustomerId(1L)).thenReturn(Flux.just(doc1));
        when(phoneNumberRepository.findByCustomerId(2L)).thenReturn(Flux.just(phone2));
        when(documentRepository.findByCustomerId(2L)).thenReturn(Flux.empty()); // Jane has no documents

        StepVerifier.create(customerService.getAllCustomers())
                .expectNextMatches(c -> c.getId().equals(1L) && !c.getPhoneNumbers().isEmpty() && !c.getDocuments().isEmpty())
                .expectNextMatches(c -> c.getId().equals(2L) && !c.getPhoneNumbers().isEmpty() && c.getDocuments().isEmpty())
                .verifyComplete();
    }

    @Test
    void testGetCustomerById_whenExists() {
        Long customerId = 1L;
        Customer customer = new Customer(customerId, "John Doe", "12345", "Street 1", "City A", "Region X", null, null);
        PhoneNumber phone = new PhoneNumber(101L, "555-0101", PhoneNumberType.PRIMARY, customerId);
        Document doc = new Document(201L, DocumentType.NATIONAL_ID_COPY, "/path/to/id.jpg", customerId);

        when(customerRepository.findById(customerId)).thenReturn(Mono.just(customer));
        when(phoneNumberRepository.findByCustomerId(customerId)).thenReturn(Flux.just(phone));
        when(documentRepository.findByCustomerId(customerId)).thenReturn(Flux.just(doc));

        StepVerifier.create(customerService.getCustomerById(customerId))
                .expectNextMatches(c -> c.getId().equals(customerId) &&
                        c.getPhoneNumbers().size() == 1 && c.getPhoneNumbers().get(0).getNumber().equals("555-0101") &&
                        c.getDocuments().size() == 1 && c.getDocuments().get(0).getFilePathOrData().equals("/path/to/id.jpg"))
                .verifyComplete();
    }

    @Test
    void testGetCustomerById_whenNotExists() {
        Long customerId = 1L;
        when(customerRepository.findById(customerId)).thenReturn(Mono.empty());

        StepVerifier.create(customerService.getCustomerById(customerId))
                .verifyComplete();

        verify(phoneNumberRepository, never()).findByCustomerId(anyLong());
        verify(documentRepository, never()).findByCustomerId(anyLong());
    }

    @Test
    void testCreateCustomer_withPhonesAndDocuments() {
        PhoneNumber inputPhone = new PhoneNumber(null, "555-0103", PhoneNumberType.PRIMARY, null); // No ID, no customerId
        Document inputDoc = new Document(null, DocumentType.PASSPORT_COPY, "/path/new_passport.jpg", null);
        Customer customerToCreate = new Customer(null, "Alice Wonderland", "54321", "Rabbit Hole", "Wonderland", "Oz",
                Collections.singletonList(inputPhone), Collections.singletonList(inputDoc));

        Customer savedCustomer = new Customer(1L, "Alice Wonderland", "54321", "Rabbit Hole", "Wonderland", "Oz", null, null);
        PhoneNumber savedPhone = new PhoneNumber(103L, "555-0103", PhoneNumberType.PRIMARY, 1L);
        Document savedDoc = new Document(203L, DocumentType.PASSPORT_COPY, "/path/new_passport.jpg", 1L);

        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            c.setId(savedCustomer.getId()); // Simulate ID generation
            return Mono.just(c);
        });
        when(phoneNumberRepository.save(any(PhoneNumber.class))).thenAnswer(invocation -> {
            PhoneNumber p = invocation.getArgument(0);
            p.setId(savedPhone.getId()); // Simulate ID generation
            // customerId should be set by the service before save
            return Mono.just(p);
        });
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document d = invocation.getArgument(0);
            d.setId(savedDoc.getId()); // Simulate ID generation
            // customerId should be set by the service before save
            return Mono.just(d);
        });

        StepVerifier.create(customerService.createCustomer(customerToCreate))
                .expectNextMatches(c -> c.getId().equals(1L) &&
                        c.getPhoneNumbers() != null && c.getPhoneNumbers().size() == 1 &&
                        c.getPhoneNumbers().get(0).getId().equals(103L) &&
                        c.getPhoneNumbers().get(0).getCustomerId().equals(1L) &&
                        c.getDocuments() != null && c.getDocuments().size() == 1 &&
                        c.getDocuments().get(0).getId().equals(203L) &&
                        c.getDocuments().get(0).getCustomerId().equals(1L))
                .verifyComplete();

        verify(customerRepository).save(any(Customer.class));
        verify(phoneNumberRepository).save(any(PhoneNumber.class));
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    void testCreateCustomer_withoutPhonesOrDocuments() {
        Customer customerToCreate = new Customer(null, "Bob The Builder", "00000", "Build Street", "Constructville", "ToolTopia",
                null, Collections.emptyList()); // null phones, empty documents

        Customer savedCustomer = new Customer(1L, "Bob The Builder", "00000", "Build Street", "Constructville", "ToolTopia", null, null);

        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer c = invocation.getArgument(0);
            c.setId(savedCustomer.getId()); // Simulate ID generation
            return Mono.just(c);
        });

        StepVerifier.create(customerService.createCustomer(customerToCreate))
                .expectNextMatches(c -> c.getId().equals(1L) &&
                        (c.getPhoneNumbers() == null || c.getPhoneNumbers().isEmpty()) &&
                        (c.getDocuments() == null || c.getDocuments().isEmpty()))
                .verifyComplete();

        verify(customerRepository).save(any(Customer.class));
        verify(phoneNumberRepository, never()).save(any(PhoneNumber.class));
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void testUpdateCustomer_whenExists() {
        Long customerId = 1L;
        Customer existingCustomer = new Customer(customerId, "Old Name", "OldNatId", "Old Street", "Old City", "Old Region", null, null);
        Customer customerDetails = new Customer(null, "New Name", "NewNatId", "New Street", "New City", "New Region", null, null); // Details for update

        // Updated direct fields, but transient fields are populated from existing
        Customer savedCustomer = new Customer(customerId, "New Name", "NewNatId", "New Street", "New City", "New Region", null, null);

        PhoneNumber existingPhone = new PhoneNumber(101L, "555-0101", PhoneNumberType.PRIMARY, customerId);
        Document existingDoc = new Document(201L, DocumentType.NATIONAL_ID_COPY, "/path/id.jpg", customerId);

        when(customerRepository.findById(customerId)).thenReturn(Mono.just(existingCustomer));
        when(customerRepository.save(any(Customer.class))).thenReturn(Mono.just(savedCustomer)); // save returns the merged customer
        when(phoneNumberRepository.findByCustomerId(customerId)).thenReturn(Flux.just(existingPhone));
        when(documentRepository.findByCustomerId(customerId)).thenReturn(Flux.just(existingDoc));

        StepVerifier.create(customerService.updateCustomer(customerId, customerDetails))
                .expectNextMatches(c -> c.getId().equals(customerId) &&
                        c.getFullName().equals("New Name") &&
                        c.getNationalId().equals("NewNatId") &&
                        c.getPhoneNumbers().size() == 1 &&
                        c.getDocuments().size() == 1)
                .verifyComplete();

        verify(customerRepository).save(argThat(c -> "New Name".equals(c.getFullName())));
    }

    @Test
    void testUpdateCustomer_whenNotExists() {
        Long customerId = 1L;
        Customer customerDetails = new Customer(null, "Non Existent", "NE", "NE Street", "NE City", "NE Region", null, null);
        when(customerRepository.findById(customerId)).thenReturn(Mono.empty());

        StepVerifier.create(customerService.updateCustomer(customerId, customerDetails))
                .verifyComplete();

        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void testDeleteCustomer() {
        Long customerId = 1L;
        when(customerRepository.deleteById(customerId)).thenReturn(Mono.empty()); // Mono<Void>

        StepVerifier.create(customerService.deleteCustomer(customerId))
                .verifyComplete();

        verify(customerRepository).deleteById(customerId);
    }
}
