package com.example.simpleerpsystem.maintenance.controller;

import com.example.simpleerpsystem.customer.entity.Customer;
import com.example.simpleerpsystem.customer.repository.CustomerRepository;
import com.example.simpleerpsystem.entity.Product;
import com.example.simpleerpsystem.entity.enums.ProductCategory;
import com.example.simpleerpsystem.maintenance.entity.ServiceRecord;
import com.example.simpleerpsystem.maintenance.entity.enums.ServiceType;
import com.example.simpleerpsystem.maintenance.repository.ServiceRecordRepository;
import com.example.simpleerpsystem.repository.ProductRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MaintenanceControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ServiceRecordRepository serviceRecordRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;

    private Customer testCustomer1, testCustomer2;
    private Product testProduct1, testProduct2;

    @BeforeEach
    void setUp() {
        serviceRecordRepository.deleteAll().block();
        customerRepository.deleteAll().block();
        productRepository.deleteAll().block();

        testCustomer1 = customerRepository.save(Customer.builder().fullName("Maint Cust 1").nationalId("M001").build()).block();
        testCustomer2 = customerRepository.save(Customer.builder().fullName("Maint Cust 2").nationalId("M002").build()).block();
        testProduct1 = productRepository.save(Product.builder().name("Maint Prod 1").price(100.0).quantityOnHand(10).category(ProductCategory.FILTER_PART).lowStockThreshold(2).build()).block();
        testProduct2 = productRepository.save(Product.builder().name("Maint Prod 2").price(200.0).quantityOnHand(20).category(ProductCategory.AC_PART).lowStockThreshold(3).build()).block();

        assertNotNull(testCustomer1);
        assertNotNull(testCustomer2);
        assertNotNull(testProduct1);
        assertNotNull(testProduct2);
    }

    @Test
    @Order(1)
    void testCreateServiceRecord_Success_WithProduct() {
        ServiceRecord newRecord = ServiceRecord.builder()
                .customerId(testCustomer1.getId())
                .productId(testProduct1.getId())
                .serviceType(ServiceType.REPAIR_WATER_FILTER)
                .description("Filter replacement")
                .serviceDate(LocalDate.now())
                .cost(75.50)
                // isFree will be defaulted by service based on cost
                .notes("Customer reported low pressure.")
                .build();

        webTestClient.post().uri("/api/v1/maintenance/service-records")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(newRecord), ServiceRecord.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceRecord.class)
                .value(sr -> {
                    assertNotNull(sr.getId());
                    assertEquals(newRecord.getCustomerId(), sr.getCustomerId());
                    assertEquals(newRecord.getProductId(), sr.getProductId());
                    assertEquals(newRecord.getServiceType(), sr.getServiceType());
                    assertEquals(newRecord.getDescription(), sr.getDescription());
                    assertEquals(newRecord.getServiceDate(), sr.getServiceDate());
                    assertEquals(newRecord.getCost(), sr.getCost());
                    assertFalse(sr.getIsFree()); // Cost is > 0
                    assertEquals(newRecord.getNotes(), sr.getNotes());
                });

        StepVerifier.create(serviceRecordRepository.count())
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    @Order(2)
    void testCreateServiceRecord_Success_FreeService_NoProduct() {
        ServiceRecord newRecord = ServiceRecord.builder()
                .customerId(testCustomer2.getId())
                .serviceType(ServiceType.CONSULTATION)
                .description("Free initial consultation")
                .serviceDate(LocalDate.now())
                .cost(0.0) // Explicitly 0.0
                // isFree will be defaulted
                .build();

        webTestClient.post().uri("/api/v1/maintenance/service-records")
                .body(Mono.just(newRecord), ServiceRecord.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ServiceRecord.class)
                .value(sr -> {
                    assertNotNull(sr.getId());
                    assertEquals(newRecord.getCustomerId(), sr.getCustomerId());
                    assertNull(sr.getProductId());
                    assertEquals(newRecord.getServiceType(), sr.getServiceType());
                    assertEquals(0.0, sr.getCost());
                    assertTrue(sr.getIsFree());
                });
    }

    @Test
    @Order(3)
    void testCreateServiceRecord_Fail_CustomerNotFound() {
        ServiceRecord newRecord = ServiceRecord.builder()
                .customerId(9999L) // Non-existent customer
                .serviceType(ServiceType.OTHER)
                .description("Test")
                .serviceDate(LocalDate.now())
                .cost(10.0)
                .build();

        webTestClient.post().uri("/api/v1/maintenance/service-records")
                .body(Mono.just(newRecord), ServiceRecord.class)
                .exchange()
                .expectStatus().is5xxServerError(); // Or specific error if controller advice is present
    }

    private ServiceRecord createSampleRecord(Long customerId, Long productId, ServiceType type, double cost) {
        return serviceRecordRepository.save(ServiceRecord.builder()
                .customerId(customerId)
                .productId(productId)
                .serviceType(type)
                .description("Sample " + type)
                .serviceDate(LocalDate.now())
                .cost(cost)
                .isFree(cost == 0.0)
                .build()).block();
    }

    @Test
    @Order(4)
    void testGetServiceRecordById_Success() {
        ServiceRecord record = createSampleRecord(testCustomer1.getId(), testProduct1.getId(), ServiceType.CLEANING, 20.0);
        assertNotNull(record);

        webTestClient.get().uri("/api/v1/maintenance/service-records/" + record.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceRecord.class)
                .value(sr -> assertEquals(record.getId(), sr.getId()));
    }

    @Test
    @Order(5)
    void testGetServiceRecordById_NotFound() {
        webTestClient.get().uri("/api/v1/maintenance/service-records/99999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(6)
    void testGetAllServiceRecords() {
        createSampleRecord(testCustomer1.getId(), testProduct1.getId(), ServiceType.INSPECTION, 0.0);
        createSampleRecord(testCustomer2.getId(), testProduct2.getId(), ServiceType.PART_REPLACEMENT, 150.0);

        webTestClient.get().uri("/api/v1/maintenance/service-records")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceRecord.class).hasSize(2);
    }

    @Test
    @Order(7)
    void testUpdateServiceRecord_Success() {
        ServiceRecord record = createSampleRecord(testCustomer1.getId(), testProduct1.getId(), ServiceType.CLEANING, 30.0);
        assertNotNull(record);

        ServiceRecord updateDetails = ServiceRecord.builder()
                .customerId(testCustomer1.getId()) // Keep same customer
                .productId(testProduct2.getId())   // Change product
                .serviceType(ServiceType.REPAIR_AC_UNIT)
                .description("Updated AC repair")
                .serviceDate(LocalDate.now().plusDays(1))
                .cost(120.0)
                .isFree(false)
                .notes("More complex than expected.")
                .build();

        webTestClient.put().uri("/api/v1/maintenance/service-records/" + record.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(updateDetails), ServiceRecord.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ServiceRecord.class)
                .value(sr -> {
                    assertEquals(record.getId(), sr.getId());
                    assertEquals(updateDetails.getProductId(), sr.getProductId());
                    assertEquals(updateDetails.getServiceType(), sr.getServiceType());
                    assertEquals(updateDetails.getDescription(), sr.getDescription());
                    assertEquals(120.0, sr.getCost());
                });
    }

    @Test
    @Order(8)
    void testUpdateServiceRecord_NotFound() {
        ServiceRecord updateDetails = ServiceRecord.builder().description("No matter").build();
        webTestClient.put().uri("/api/v1/maintenance/service-records/99999")
                .body(Mono.just(updateDetails), ServiceRecord.class)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(9)
    void testDeleteServiceRecord_Success() {
        ServiceRecord record = createSampleRecord(testCustomer1.getId(), null, ServiceType.CONSULTATION, 10.0);
        assertNotNull(record);

        webTestClient.delete().uri("/api/v1/maintenance/service-records/" + record.getId())
                .exchange()
                .expectStatus().isNoContent();

        StepVerifier.create(serviceRecordRepository.findById(record.getId()))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @Order(10)
    void testDeleteServiceRecord_NotFoundResponse() {
        webTestClient.delete().uri("/api/v1/maintenance/service-records/99999")
                .exchange()
                .expectStatus().isNoContent(); // Or .isNotFound() if preferred for DELETE on non-existent
    }

    @Test
    @Order(11)
    void testGetServiceRecordsByCustomerId() {
        createSampleRecord(testCustomer1.getId(), testProduct1.getId(), ServiceType.CLEANING, 25.0);
        createSampleRecord(testCustomer2.getId(), testProduct2.getId(), ServiceType.REPAIR_AC_UNIT, 225.0);
        createSampleRecord(testCustomer1.getId(), testProduct2.getId(), ServiceType.INSPECTION, 0.0);

        webTestClient.get().uri("/api/v1/maintenance/service-records/customer/" + testCustomer1.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceRecord.class).hasSize(2)
                .value(list -> list.forEach(sr -> assertEquals(testCustomer1.getId(), sr.getCustomerId())));
    }

    @Test
    @Order(12)
    void testGetServiceRecordsByProductId() {
        createSampleRecord(testCustomer1.getId(), testProduct1.getId(), ServiceType.PART_REPLACEMENT, 80.0);
        createSampleRecord(testCustomer2.getId(), testProduct2.getId(), ServiceType.INSTALLATION_AC_UNIT, 300.0);
        createSampleRecord(testCustomer1.getId(), testProduct1.getId(), ServiceType.CONSULTATION, 15.0);

        webTestClient.get().uri("/api/v1/maintenance/service-records/product/" + testProduct1.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceRecord.class).hasSize(2)
                .value(list -> list.forEach(sr -> assertEquals(testProduct1.getId(), sr.getProductId())));
    }

    @Test
    @Order(13)
    void testGetServiceRecordsByDateRange() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        createSampleRecord(testCustomer1.getId(), null, ServiceType.OTHER, 10.0).setServiceDate(yesterday);
        serviceRecordRepository.save(createSampleRecord(testCustomer1.getId(), null, ServiceType.OTHER, 10.0).setServiceDate(yesterday)).block();
        serviceRecordRepository.save(createSampleRecord(testCustomer2.getId(), null, ServiceType.INSPECTION, 0.0).setServiceDate(today)).block();
        serviceRecordRepository.save(createSampleRecord(testCustomer1.getId(), null, ServiceType.CLEANING, 20.0).setServiceDate(tomorrow)).block();


        webTestClient.get().uri(uriBuilder -> uriBuilder
                .path("/api/v1/maintenance/service-records/date-range")
                .queryParam("startDate", yesterday.toString())
                .queryParam("endDate", today.toString())
                .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(ServiceRecord.class).hasSize(2) // yesterday and today
                .value(list -> {
                    assertTrue(list.stream().anyMatch(sr -> sr.getServiceDate().equals(yesterday)));
                    assertTrue(list.stream().anyMatch(sr -> sr.getServiceDate().equals(today)));
                    assertFalse(list.stream().anyMatch(sr -> sr.getServiceDate().equals(tomorrow)));
                });
    }
}
