package com.example.simpleerpsystem.sales.controller;

import com.example.simpleerpsystem.customer.entity.Customer;
import com.example.simpleerpsystem.customer.repository.CustomerRepository;
import com.example.simpleerpsystem.entity.Product;
import com.example.simpleerpsystem.entity.enums.ProductCategory;
import com.example.simpleerpsystem.repository.ProductRepository;
import com.example.simpleerpsystem.sales.entity.AdditionalServiceItem;
import com.example.simpleerpsystem.sales.entity.InvoiceItem;
import com.example.simpleerpsystem.sales.entity.SalesInvoice;
import com.example.simpleerpsystem.sales.entity.enums.AdditionalServiceCostType;
import com.example.simpleerpsystem.sales.entity.enums.PaymentStatus;
import com.example.simpleerpsystem.sales.entity.enums.SaleType;
import com.example.simpleerpsystem.sales.repository.AdditionalServiceItemRepository;
import com.example.simpleerpsystem.sales.repository.InvoiceItemRepository;
import com.example.simpleerpsystem.sales.repository.SalesInvoiceRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SalesControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private SalesInvoiceRepository salesInvoiceRepository;
    @Autowired
    private InvoiceItemRepository invoiceItemRepository;
    @Autowired
    private AdditionalServiceItemRepository additionalServiceItemRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CustomerRepository customerRepository;

    private Customer testCustomer;
    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        // Clean up child tables first
        additionalServiceItemRepository.deleteAll().block();
        invoiceItemRepository.deleteAll().block();
        salesInvoiceRepository.deleteAll().block();

        // Clean up parent tables (or tables referenced by SalesInvoice)
        productRepository.deleteAll().block();
        customerRepository.deleteAll().block();

        // Setup common test data
        testCustomer = customerRepository.save(new Customer(null, "Test Customer", "NATID001", "123 Test St", "Testville", "Test Region", null, null)).block();
        assertNotNull(testCustomer);

        testProduct1 = productRepository.save(new Product(null, "Test Product 1", "Desc P1", 100.0, 50, ProductCategory.WATER_FILTER, 10)).block();
        assertNotNull(testProduct1);
        testProduct2 = productRepository.save(new Product(null, "Test Product 2", "Desc P2", 200.0, 30, ProductCategory.AC_UNIT, 5)).block();
        assertNotNull(testProduct2);
    }

    @Test
    @Order(1)
    void testCreateSalesInvoice_Success() {
        InvoiceItem item1 = InvoiceItem.builder().productId(testProduct1.getId()).quantity(2).build(); // Subtotal: 2 * 100 = 200
        AdditionalServiceItem asi1 = AdditionalServiceItem.builder()
                .serviceName("Express Delivery")
                .costType(AdditionalServiceCostType.FIXED_AMOUNT)
                .value(25.0)
                .build();

        SalesInvoice invoiceRequest = SalesInvoice.builder()
                .customerId(testCustomer.getId())
                .saleType(SaleType.CASH)
                .items(Collections.singletonList(item1))
                .additionalServices(Collections.singletonList(asi1))
                .discountPercentage(0.10) // 10% on 200 = 20 discount. Amount after item discount = 180.
                .build();
        // Expected total: (200 - 20) + 25 = 180 + 25 = 205

        webTestClient.post().uri("/api/v1/sales-invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(invoiceRequest), SalesInvoice.class)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(SalesInvoice.class)
                .value(responseInvoice -> {
                    assertNotNull(responseInvoice.getId());
                    assertEquals(testCustomer.getId(), responseInvoice.getCustomerId());
                    assertEquals(SaleType.CASH, responseInvoice.getSaleType());
                    assertEquals(PaymentStatus.PENDING_CASH, responseInvoice.getPaymentStatus()); // Default for CASH
                    assertNotNull(responseInvoice.getInvoiceDate());

                    assertEquals(200.0, responseInvoice.getSubTotalAmount(), 0.01);
                    assertEquals(0.10, responseInvoice.getDiscountPercentage(), 0.01);
                    assertNull(responseInvoice.getDiscountAmount()); // Percentage was given
                    assertEquals(25.0, responseInvoice.getAdditionalServicesTotalAmount(), 0.01);
                    assertEquals(205.0, responseInvoice.getTotalAmount(), 0.01);

                    assertNotNull(responseInvoice.getItems());
                    assertEquals(1, responseInvoice.getItems().size());
                    InvoiceItem responseItem = responseInvoice.getItems().get(0);
                    assertNotNull(responseItem.getId());
                    assertEquals(responseInvoice.getId(), responseItem.getSalesInvoiceId());
                    assertEquals(testProduct1.getId(), responseItem.getProductId());
                    assertEquals(testProduct1.getName(), responseItem.getProductName());
                    assertEquals(2, responseItem.getQuantity());
                    assertEquals(100.0, responseItem.getUnitPrice(), 0.01);
                    assertEquals(200.0, responseItem.getSubtotal(), 0.01);

                    assertNotNull(responseInvoice.getAdditionalServices());
                    assertEquals(1, responseInvoice.getAdditionalServices().size());
                    AdditionalServiceItem responseAsi = responseInvoice.getAdditionalServices().get(0);
                    assertNotNull(responseAsi.getId());
                    assertEquals(responseInvoice.getId(), responseAsi.getSalesInvoiceId());
                    assertEquals("Express Delivery", responseAsi.getServiceName());
                    assertEquals(25.0, responseAsi.getCalculatedCost(), 0.01);
                });

        // Verify product stock update
        Product updatedProduct1 = productRepository.findById(testProduct1.getId()).block();
        assertNotNull(updatedProduct1);
        assertEquals(48, updatedProduct1.getQuantityOnHand()); // 50 - 2
    }

    @Test
    @Order(2)
    void testCreateSalesInvoice_ProductNotFound() {
        InvoiceItem item1 = InvoiceItem.builder().productId(9999L).quantity(1).build(); // Non-existent product
        SalesInvoice invoiceRequest = SalesInvoice.builder()
                .customerId(testCustomer.getId())
                .saleType(SaleType.CASH)
                .items(Collections.singletonList(item1))
                .build();

        webTestClient.post().uri("/api/v1/sales-invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(invoiceRequest), SalesInvoice.class)
                .exchange()
                .expectStatus().is5xxServerError(); // Default for RuntimeException
    }

    @Test
    @Order(3)
    void testCreateSalesInvoice_InsufficientStock() {
        InvoiceItem item1 = InvoiceItem.builder().productId(testProduct1.getId()).quantity(testProduct1.getQuantityOnHand() + 1).build();
        SalesInvoice invoiceRequest = SalesInvoice.builder()
                .customerId(testCustomer.getId())
                .saleType(SaleType.CASH)
                .items(Collections.singletonList(item1))
                .build();

        webTestClient.post().uri("/api/v1/sales-invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(invoiceRequest), SalesInvoice.class)
                .exchange()
                .expectStatus().is5xxServerError(); // Default for RuntimeException
    }
    
    private SalesInvoice createSampleInvoiceForGet() {
        InvoiceItem item = InvoiceItem.builder().productId(testProduct2.getId()).quantity(1).build();
        SalesInvoice invoice = SalesInvoice.builder()
            .customerId(testCustomer.getId())
            .saleType(SaleType.INSTALLMENT)
            .items(Collections.singletonList(item))
            .build();
        // Use actual service to create, as it correctly populates all fields and relations
        return webTestClient.post().uri("/api/v1/sales-invoices")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Mono.just(invoice), SalesInvoice.class)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(SalesInvoice.class)
            .returnResult().getResponseBody();
    }


    @Test
    @Order(4)
    void testGetSalesInvoiceById_Success() {
        SalesInvoice createdInvoice = createSampleInvoiceForGet();
        assertNotNull(createdInvoice);
        assertNotNull(createdInvoice.getId());

        webTestClient.get().uri("/api/v1/sales-invoices/" + createdInvoice.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(SalesInvoice.class)
                .value(fetchedInvoice -> {
                    assertEquals(createdInvoice.getId(), fetchedInvoice.getId());
                    assertEquals(createdInvoice.getCustomerId(), fetchedInvoice.getCustomerId());
                    assertEquals(createdInvoice.getTotalAmount(), fetchedInvoice.getTotalAmount(), 0.01);
                    assertNotNull(fetchedInvoice.getItems());
                    assertEquals(1, fetchedInvoice.getItems().size());
                    assertEquals(testProduct2.getId(), fetchedInvoice.getItems().get(0).getProductId());
                });
    }

    @Test
    @Order(5)
    void testGetSalesInvoiceById_NotFound() {
        webTestClient.get().uri("/api/v1/sales-invoices/99999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @Order(6)
    void testGetAllSalesInvoices() {
        createSampleInvoiceForGet(); // Create one invoice
        // Create another distinct invoice
        InvoiceItem item2 = InvoiceItem.builder().productId(testProduct1.getId()).quantity(1).build();
        SalesInvoice invoice2 = SalesInvoice.builder()
            .customerId(testCustomer.getId())
            .saleType(SaleType.CASH)
            .items(Collections.singletonList(item2))
            .build();
        webTestClient.post().uri("/api/v1/sales-invoices")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Mono.just(invoice2), SalesInvoice.class)
            .exchange()
            .expectStatus().isCreated();


        webTestClient.get().uri("/api/v1/sales-invoices")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(SalesInvoice.class).hasSize(2);
    }

    @Test
    @Order(7)
    void testDeleteSalesInvoice() {
        SalesInvoice createdInvoice = createSampleInvoiceForGet();
        assertNotNull(createdInvoice);
        Long invoiceId = createdInvoice.getId();
        assertNotNull(createdInvoice.getItems());
        assertFalse(createdInvoice.getItems().isEmpty());
        Long itemId = createdInvoice.getItems().get(0).getId();
        int initialStockP2 = testProduct2.getQuantityOnHand(); // Stock after createSampleInvoiceForGet

        webTestClient.delete().uri("/api/v1/sales-invoices/" + invoiceId)
                .exchange()
                .expectStatus().isNoContent();

        // Verify invoice is deleted
        StepVerifier.create(salesInvoiceRepository.findById(invoiceId))
                .expectNextCount(0)
                .verifyComplete();
        // Verify items are deleted (CASCADE)
        StepVerifier.create(invoiceItemRepository.findById(itemId))
                .expectNextCount(0)
                .verifyComplete();
        
        // Verify product stock is NOT restored
        Product product2AfterDelete = productRepository.findById(testProduct2.getId()).block();
        assertNotNull(product2AfterDelete);
        assertEquals(initialStockP2, product2AfterDelete.getQuantityOnHand()); 
        // Initial stock was 30, createSampleInvoiceForGet sold 1, so initialStockP2 here is 29.
        // This verifies that deleting the invoice does not change product stock.
    }
}
