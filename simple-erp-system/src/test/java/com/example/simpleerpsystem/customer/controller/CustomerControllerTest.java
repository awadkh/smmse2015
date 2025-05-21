package com.example.simpleerpsystem.customer.controller;

import com.example.simpleerpsystem.customer.entity.Customer;
import com.example.simpleerpsystem.customer.entity.Document;
import com.example.simpleerpsystem.customer.entity.PhoneNumber;
import com.example.simpleerpsystem.customer.entity.enums.DocumentType;
import com.example.simpleerpsystem.customer.entity.enums.PhoneNumberType;
import com.example.simpleerpsystem.customer.repository.CustomerRepository;
import com.example.simpleerpsystem.customer.repository.DocumentRepository;
import com.example.simpleerpsystem.customer.repository.PhoneNumberRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // Optional, as requested
public class CustomerControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PhoneNumberRepository phoneNumberRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @BeforeEach
    void setUp() {
        // Order is important due to foreign key constraints with ON DELETE CASCADE
        phoneNumberRepository.deleteAll().block();
        documentRepository.deleteAll().block();
        customerRepository.deleteAll().block();
    }

    @Test
    @Order(1)
    void testCreateCustomer_withPhoneNumberAndDocument_andGetById() {
        PhoneNumber newPhone = new PhoneNumber(null, "555-0202", PhoneNumberType.PRIMARY, null);
        Document newDoc = new Document(null, DocumentType.NATIONAL_ID_COPY, "/path/to/jane_id.png", null);
        Customer newCustomer = new Customer(
                null, "Jane Doe", "67890", "456 Oak St", "Otherville", "Region Y",
                Collections.singletonList(newPhone),
                Collections.singletonList(newDoc)
        );

        // POST to create customer
        Customer createdCustomer = webTestClient.post().uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newCustomer), Customer.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Customer.class)
                .value(customer -> {
                    assertNotNull(customer.getId());
                    assertEquals("Jane Doe", customer.getFullName());
                    assertEquals("67890", customer.getNationalId());
                    assertNotNull(customer.getPhoneNumbers());
                    assertEquals(1, customer.getPhoneNumbers().size());
                    assertNotNull(customer.getPhoneNumbers().get(0).getId());
                    assertEquals("555-0202", customer.getPhoneNumbers().get(0).getNumber());
                    assertEquals(PhoneNumberType.PRIMARY, customer.getPhoneNumbers().get(0).getType());
                    assertNotNull(customer.getDocuments());
                    assertEquals(1, customer.getDocuments().size());
                    assertNotNull(customer.getDocuments().get(0).getId());
                    assertEquals(DocumentType.NATIONAL_ID_COPY, customer.getDocuments().get(0).getDocumentType());
                    assertEquals("/path/to/jane_id.png", customer.getDocuments().get(0).getFilePathOrData());
                })
                .returnResult().getResponseBody();

        assertNotNull(createdCustomer);
        Long customerId = createdCustomer.getId();

        // GET by ID to verify
        webTestClient.get().uri("/api/v1/customers/" + customerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Customer.class)
                .value(fetchedCustomer -> {
                    assertEquals(customerId, fetchedCustomer.getId());
                    assertEquals("Jane Doe", fetchedCustomer.getFullName());
                    assertEquals("67890", fetchedCustomer.getNationalId());
                    assertNotNull(fetchedCustomer.getPhoneNumbers());
                    assertEquals(1, fetchedCustomer.getPhoneNumbers().size());
                    assertEquals(createdCustomer.getPhoneNumbers().get(0).getId(), fetchedCustomer.getPhoneNumbers().get(0).getId());
                    assertEquals("555-0202", fetchedCustomer.getPhoneNumbers().get(0).getNumber());
                    assertNotNull(fetchedCustomer.getDocuments());
                    assertEquals(1, fetchedCustomer.getDocuments().size());
                    assertEquals(createdCustomer.getDocuments().get(0).getId(), fetchedCustomer.getDocuments().get(0).getId());
                    assertEquals("/path/to/jane_id.png", fetchedCustomer.getDocuments().get(0).getFilePathOrData());
                });
    }

    @Test
    @Order(2)
    void testCreateCustomer_withoutPhonesOrDocuments() {
        Customer newCustomer = new Customer(
                null, "Bob The Builder", "00000", "Construct St", "Buildville", "Region Z",
                null, // No phone numbers
                Collections.emptyList() // Empty documents list
        );

        webTestClient.post().uri("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newCustomer), Customer.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Customer.class)
                .value(customer -> {
                    assertNotNull(customer.getId());
                    assertEquals("Bob The Builder", customer.getFullName());
                    assertTrue(customer.getPhoneNumbers() == null || customer.getPhoneNumbers().isEmpty());
                    assertTrue(customer.getDocuments() == null || customer.getDocuments().isEmpty());
                });
    }

    @Test
    @Order(3)
    void testGetAllCustomers() {
        // Customer 1: with phone and doc
        Customer customer1 = new Customer(null, "Alice Wonderland", "11111", "1 Wonderland Ave", "TeaParty Town", "Region A", null, null);
        PhoneNumber phone1 = new PhoneNumber(null, "555-1111", PhoneNumberType.PRIMARY, null);
        Document doc1 = new Document(null, DocumentType.PASSPORT_COPY, "/alice/passport.jpg", null);
        customer1.setPhoneNumbers(Collections.singletonList(phone1));
        customer1.setDocuments(Collections.singletonList(doc1));
        // Save via service to ensure relations are set up correctly for this test's purpose
        customerService.createCustomer(customer1).block();


        // Customer 2: without phone or doc
        Customer customer2 = new Customer(null, "Charlie Chaplin", "22222", "2 Silent Film Rd", "Hollywood", "Region B", new ArrayList<>(), new ArrayList<>());
        customerService.createCustomer(customer2).block();


        webTestClient.get().uri("/api/v1/customers")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Customer.class).hasSize(2)
                .consumeWith(response -> {
                    List<Customer> customers = response.getResponseBody();
                    assertNotNull(customers);
                    Customer alice = customers.stream().filter(c -> "Alice Wonderland".equals(c.getFullName())).findFirst().orElse(null);
                    Customer charlie = customers.stream().filter(c -> "Charlie Chaplin".equals(c.getFullName())).findFirst().orElse(null);

                    assertNotNull(alice);
                    assertNotNull(alice.getPhoneNumbers());
                    assertEquals(1, alice.getPhoneNumbers().size());
                    assertEquals("555-1111", alice.getPhoneNumbers().get(0).getNumber());
                    assertNotNull(alice.getDocuments());
                    assertEquals(1, alice.getDocuments().size());
                    assertEquals("/alice/passport.jpg", alice.getDocuments().get(0).getFilePathOrData());

                    assertNotNull(charlie);
                    assertTrue(charlie.getPhoneNumbers() == null || charlie.getPhoneNumbers().isEmpty());
                    assertTrue(charlie.getDocuments() == null || charlie.getDocuments().isEmpty());
                });
    }
    // Helper to use CustomerService directly for setup in testGetAllCustomers, as WebTestClient POST might be complex for a list
    @Autowired com.example.simpleerpsystem.customer.service.CustomerService customerService;


    @Test
    @Order(4)
    void testGetCustomerById_notFound() {
        webTestClient.get().uri("/api/v1/customers/99999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(5)
    void testUpdateCustomer_directFields() {
        // Create initial customer
        PhoneNumber initialPhone = new PhoneNumber(null, "555-ORIGINAL", PhoneNumberType.PRIMARY, null);
        Customer initialCustomer = new Customer(null, "Old Name", "NATID123", "Old Street", "Old City", "Old Region", Collections.singletonList(initialPhone), null);
        Customer savedCustomer = customerService.createCustomer(initialCustomer).block();
        assertNotNull(savedCustomer);
        Long customerId = savedCustomer.getId();
        assertNotNull(savedCustomer.getPhoneNumbers());
        assertFalse(savedCustomer.getPhoneNumbers().isEmpty());
        Long phoneId = savedCustomer.getPhoneNumbers().get(0).getId();


        Customer customerUpdateDetails = new Customer(null, "New Name", "NATID123", "New Street", "New City", "New Region", null, null);
        // Note: phoneNumbers and documents in customerUpdateDetails are null/empty.
        // Service's update method updates direct fields and then re-populates existing relations.

        webTestClient.put().uri("/api/v1/customers/" + customerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(customerUpdateDetails), Customer.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Customer.class)
                .value(updatedCustomer -> {
                    assertEquals(customerId, updatedCustomer.getId());
                    assertEquals("New Name", updatedCustomer.getFullName());
                    assertEquals("New Street", updatedCustomer.getAddressStreet());
                    assertEquals("New City", updatedCustomer.getAddressCity());
                    assertNotNull(updatedCustomer.getPhoneNumbers());
                    assertEquals(1, updatedCustomer.getPhoneNumbers().size()); // Existing phone should still be there
                    assertEquals(phoneId, updatedCustomer.getPhoneNumbers().get(0).getId());
                    assertEquals("555-ORIGINAL", updatedCustomer.getPhoneNumbers().get(0).getNumber());
                });

        // Optionally, GET again to confirm
        webTestClient.get().uri("/api/v1/customers/" + customerId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Customer.class)
                .value(fetchedCustomer -> {
                    assertEquals("New Name", fetchedCustomer.getFullName());
                    assertNotNull(fetchedCustomer.getPhoneNumbers());
                    assertEquals(1, fetchedCustomer.getPhoneNumbers().size());
                    assertEquals("555-ORIGINAL", fetchedCustomer.getPhoneNumbers().get(0).getNumber());
                });
    }


    @Test
    @Order(6)
    void testUpdateCustomer_notFound() {
        Customer customerUpdateDetails = new Customer(null, "Non Existent", null, null, null, null, null, null);
        webTestClient.put().uri("/api/v1/customers/99999")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(customerUpdateDetails), Customer.class)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(7)
    void testDeleteCustomer() {
        // Create customer with phone and document
        PhoneNumber phone = new PhoneNumber(null, "555-DELETE", PhoneNumberType.PRIMARY, null);
        Document doc = new Document(null, DocumentType.OTHER, "some_doc_data_to_delete", null);
        Customer customerToDelete = new Customer(null, "ToBe Deleted", "DELID", "Del St", "Del City", "Del Region",
                Collections.singletonList(phone), Collections.singletonList(doc));

        Customer savedCustomer = customerService.createCustomer(customerToDelete).block();
        assertNotNull(savedCustomer);
        Long customerId = savedCustomer.getId();
        assertNotNull(savedCustomer.getPhoneNumbers());
        assertFalse(savedCustomer.getPhoneNumbers().isEmpty());
        Long phoneId = savedCustomer.getPhoneNumbers().get(0).getId();
        assertNotNull(savedCustomer.getDocuments());
        assertFalse(savedCustomer.getDocuments().isEmpty());
        Long docId = savedCustomer.getDocuments().get(0).getId();


        // DELETE the customer
        webTestClient.delete().uri("/api/v1/customers/" + customerId)
                .exchange()
                .expectStatus().isNoContent();

        // Verify customer is gone
        webTestClient.get().uri("/api/v1/customers/" + customerId)
                .exchange()
                .expectStatus().isNotFound();

        // Verify (by querying repositories directly) that associated data is also deleted (due to CASCADE)
        StepVerifier.create(customerRepository.findById(customerId))
                .expectNextCount(0)
                .verifyComplete();
        StepVerifier.create(phoneNumberRepository.findByCustomerId(customerId))
                .expectNextCount(0)
                .verifyComplete();
        StepVerifier.create(phoneNumberRepository.findById(phoneId)) // Check specific phone ID
                .expectNextCount(0)
                .verifyComplete();
        StepVerifier.create(documentRepository.findByCustomerId(customerId))
                .expectNextCount(0)
                .verifyComplete();
        StepVerifier.create(documentRepository.findById(docId)) // Check specific doc ID
                .expectNextCount(0)
                .verifyComplete();
    }
}
