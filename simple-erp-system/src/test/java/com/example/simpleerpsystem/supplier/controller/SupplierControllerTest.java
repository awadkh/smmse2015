package com.example.simpleerpsystem.supplier.controller;

import com.example.simpleerpsystem.supplier.entity.Supplier;
import com.example.simpleerpsystem.supplier.entity.SupplierContactNumber;
import com.example.simpleerpsystem.supplier.repository.SupplierContactNumberRepository;
import com.example.simpleerpsystem.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SupplierControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SupplierContactNumberRepository supplierContactNumberRepository;

    @BeforeEach
    void setUp() {
        supplierContactNumberRepository.deleteAll().block();
        supplierRepository.deleteAll().block();
    }

    @Test
    @Order(1)
    void testCreateSupplier_Success_WithContactNumbers() {
        Supplier newSupplierRequest = Supplier.builder()
                .name("New Electricals Ltd.")
                .contactPerson("Mr. Ohm")
                .email("ohm@newelec.com")
                .address("123 Circuit Lane")
                .suppliedItemsDescription("Wires, Switches, Bulbs")
                .accountBalance(100.50)
                .contactNumbers(List.of(
                        SupplierContactNumber.builder().phoneNumber("555-0001").build(),
                        SupplierContactNumber.builder().phoneNumber("555-0002").build()
                ))
                .build();

        webTestClient.post().uri("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newSupplierRequest), Supplier.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Supplier.class)
                .value(supplier -> {
                    assertNotNull(supplier.getId());
                    assertEquals("New Electricals Ltd.", supplier.getName());
                    assertEquals("ohm@newelec.com", supplier.getEmail());
                    assertEquals(100.50, supplier.getAccountBalance());
                    assertNotNull(supplier.getContactNumbers());
                    assertEquals(2, supplier.getContactNumbers().size());
                    supplier.getContactNumbers().forEach(cn -> {
                        assertNotNull(cn.getId());
                        assertEquals(supplier.getId(), cn.getSupplierId());
                        assertTrue(cn.getPhoneNumber().equals("555-0001") || cn.getPhoneNumber().equals("555-0002"));
                    });
                });

        // Verify database state
        StepVerifier.create(supplierRepository.count())
                .expectNext(1L)
                .verifyComplete();
        StepVerifier.create(supplierContactNumberRepository.count())
                .expectNext(2L) // Two contact numbers
                .verifyComplete();
    }
    
    @Test
    @Order(2)
    void testCreateSupplier_Success_NoContactNumbers_DefaultBalance() {
        Supplier newSupplierRequest = Supplier.builder()
                .name("General Supplies Co.")
                .email("contact@general.co")
                // accountBalance is null, should default to 0.0
                // contactNumbers is null
                .build();

        webTestClient.post().uri("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newSupplierRequest), Supplier.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Supplier.class)
                .value(supplier -> {
                    assertNotNull(supplier.getId());
                    assertEquals("General Supplies Co.", supplier.getName());
                    assertEquals(0.0, supplier.getAccountBalance()); // Defaulted
                    assertTrue(supplier.getContactNumbers() == null || supplier.getContactNumbers().isEmpty());
                });
    }


    @Test
    @Order(3)
    void testCreateSupplier_Fail_NameNotUnique() {
        supplierRepository.save(Supplier.builder().name("SupA").email("supa@example.com").build()).block();

        Supplier newSupplierRequest = Supplier.builder().name("SupA").email("newsupa@example.com").build();

        webTestClient.post().uri("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newSupplierRequest), Supplier.class)
                .exchange()
                .expectStatus().is5xxServerError(); // Expecting RuntimeException from service
    }

    @Test
    @Order(4)
    void testCreateSupplier_Fail_EmailNotUnique() {
        supplierRepository.save(Supplier.builder().name("SupB").email("supb@example.com").build()).block();

        Supplier newSupplierRequest = Supplier.builder().name("NewSupB").email("supb@example.com").build();

        webTestClient.post().uri("/api/v1/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newSupplierRequest), Supplier.class)
                .exchange()
                .expectStatus().is5xxServerError(); // Expecting RuntimeException from service
    }

    private Supplier createSampleSupplierInDb(String name, String email, List<String> phoneNumbers) {
        Supplier supplier = Supplier.builder().name(name).email(email).accountBalance(0.0).build();
        Supplier savedSupplier = supplierRepository.save(supplier).block();
        assertNotNull(savedSupplier);

        if (phoneNumbers != null) {
            phoneNumbers.forEach(ph -> {
                SupplierContactNumber contact = SupplierContactNumber.builder().supplierId(savedSupplier.getId()).phoneNumber(ph).build();
                supplierContactNumberRepository.save(contact).block();
            });
        }
        return savedSupplier;
    }

    @Test
    @Order(5)
    void testGetSupplierById_Success() {
        Supplier createdSupplier = createSampleSupplierInDb("GetMe Ltd", "getme@example.com", List.of("111-222"));
        assertNotNull(createdSupplier);

        webTestClient.get().uri("/api/v1/suppliers/" + createdSupplier.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Supplier.class)
                .value(supplier -> {
                    assertEquals(createdSupplier.getId(), supplier.getId());
                    assertEquals("GetMe Ltd", supplier.getName());
                    assertNotNull(supplier.getContactNumbers());
                    assertEquals(1, supplier.getContactNumbers().size());
                    assertEquals("111-222", supplier.getContactNumbers().get(0).getPhoneNumber());
                });
    }

    @Test
    @Order(6)
    void testGetSupplierById_NotFound() {
        webTestClient.get().uri("/api/v1/suppliers/99999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(7)
    void testGetAllSuppliers() {
        createSampleSupplierInDb("Supplier One", "one@example.com", List.of("111"));
        createSampleSupplierInDb("Supplier Two", "two@example.com", List.of("222", "333"));

        webTestClient.get().uri("/api/v1/suppliers")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Supplier.class).hasSize(2)
                .value(suppliers -> {
                    Supplier s1 = suppliers.stream().filter(s -> "Supplier One".equals(s.getName())).findFirst().orElse(null);
                    assertNotNull(s1);
                    assertEquals(1, s1.getContactNumbers().size());

                    Supplier s2 = suppliers.stream().filter(s -> "Supplier Two".equals(s.getName())).findFirst().orElse(null);
                    assertNotNull(s2);
                    assertEquals(2, s2.getContactNumbers().size());
                });
    }

    @Test
    @Order(8)
    void testUpdateSupplier_Success_UpdateAllDetails_IncludingContacts() {
        Supplier originalSupplier = createSampleSupplierInDb("SupOriginal", "original@example.com", List.of("111"));
        assertNotNull(originalSupplier);
        Long originalSupId = originalSupplier.getId();

        Supplier updatedSupplierRequest = Supplier.builder()
                .name("SupUpdated")
                .contactPerson("Updated Person")
                .email("updated@example.com")
                .address("Updated Address")
                .suppliedItemsDescription("Updated Items")
                .accountBalance(200.75)
                .contactNumbers(List.of(SupplierContactNumber.builder().phoneNumber("222").build()))
                .build();

        webTestClient.put().uri("/api/v1/suppliers/" + originalSupId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(updatedSupplierRequest), Supplier.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Supplier.class)
                .value(supplier -> {
                    assertEquals(originalSupId, supplier.getId());
                    assertEquals("SupUpdated", supplier.getName());
                    assertEquals("updated@example.com", supplier.getEmail());
                    assertEquals(200.75, supplier.getAccountBalance());
                    assertNotNull(supplier.getContactNumbers());
                    assertEquals(1, supplier.getContactNumbers().size());
                    assertEquals("222", supplier.getContactNumbers().get(0).getPhoneNumber());
                });

        // Verify database state
        Supplier fromDb = supplierRepository.findById(originalSupId).block();
        assertNotNull(fromDb);
        assertEquals("SupUpdated", fromDb.getName());

        List<SupplierContactNumber> contactsFromDb = supplierContactNumberRepository.findBySupplierId(originalSupId).collectList().block();
        assertNotNull(contactsFromDb);
        assertEquals(1, contactsFromDb.size());
        assertEquals("222", contactsFromDb.get(0).getPhoneNumber());
    }

    @Test
    @Order(9)
    void testUpdateSupplier_Fail_NotFound() {
        Supplier updateRequest = Supplier.builder().name("Doesn't matter").build();
        webTestClient.put().uri("/api/v1/suppliers/99999")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(updateRequest), Supplier.class)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(10)
    void testUpdateSupplier_Fail_NameConflict() {
        Supplier supA = createSampleSupplierInDb("SupA", "supa@example.com", null);
        Supplier supB = createSampleSupplierInDb("SupB", "supb@example.com", null); // Name to conflict with
        assertNotNull(supA);
        assertNotNull(supB);

        Supplier updateRequest = Supplier.builder().name("SupB").build(); // Trying to update SupA's name to SupB's name

        webTestClient.put().uri("/api/v1/suppliers/" + supA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(updateRequest), Supplier.class)
                .exchange()
                .expectStatus().is5xxServerError(); // Expecting RuntimeException
    }

    @Test
    @Order(11)
    void testDeleteSupplier_Success() {
        Supplier supplierToDelete = createSampleSupplierInDb("ToDelete", "delete@example.com", List.of("777"));
        assertNotNull(supplierToDelete);
        Long supplierId = supplierToDelete.getId();

        webTestClient.delete().uri("/api/v1/suppliers/" + supplierId)
                .exchange()
                .expectStatus().isNoContent();

        // Verify database state
        StepVerifier.create(supplierRepository.findById(supplierId))
                .expectNextCount(0)
                .verifyComplete();
        StepVerifier.create(supplierContactNumberRepository.findBySupplierId(supplierId))
                .expectNextCount(0) // Should be cascade deleted
                .verifyComplete();
    }
}
