package com.example.simpleerpsystem.installment.controller;

import com.example.simpleerpsystem.customer.entity.Customer;
import com.example.simpleerpsystem.customer.repository.CustomerRepository;
import com.example.simpleerpsystem.entity.Product;
import com.example.simpleerpsystem.entity.enums.ProductCategory;
import com.example.simpleerpsystem.installment.dto.CreateInstallmentPlanRequest;
import com.example.simpleerpsystem.installment.dto.RecordPaymentRequest;
import com.example.simpleerpsystem.installment.entity.InstallmentPayment;
import com.example.simpleerpsystem.installment.entity.InstallmentPlan;
import com.example.simpleerpsystem.installment.entity.enums.InstallmentPlanStatus;
import com.example.simpleerpsystem.installment.entity.enums.PaymentInterval;
import com.example.simpleerpsystem.installment.repository.InstallmentPaymentRepository;
import com.example.simpleerpsystem.installment.repository.InstallmentPlanRepository;
import com.example.simpleerpsystem.repository.ProductRepository;
import com.example.simpleerpsystem.sales.entity.InvoiceItem;
import com.example.simpleerpsystem.sales.entity.SalesInvoice;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InstallmentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private InstallmentPlanRepository installmentPlanRepository;
    @Autowired
    private InstallmentPaymentRepository installmentPaymentRepository;
    @Autowired
    private SalesInvoiceRepository salesInvoiceRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InvoiceItemRepository invoiceItemRepository;
    @Autowired
    private AdditionalServiceItemRepository additionalServiceItemRepository; // Though not directly used, for cleanup consistency

    private Customer customer;
    private Product product1;
    private SalesInvoice salesInvoiceInstallment;

    @BeforeEach
    void setUp() {
        // Clean up child tables first, then parent tables
        installmentPaymentRepository.deleteAll().block();
        installmentPlanRepository.deleteAll().block();
        invoiceItemRepository.deleteAll().block();
        additionalServiceItemRepository.deleteAll().block();
        salesInvoiceRepository.deleteAll().block();
        productRepository.deleteAll().block();
        customerRepository.deleteAll().block();

        customer = customerRepository.save(Customer.builder().fullName("Test Installment Cust").nationalId("98765Unique").build()).block();
        assertNotNull(customer);
        product1 = productRepository.save(Product.builder().name("Installment Product").price(1200.0).quantityOnHand(100).category(ProductCategory.AC_UNIT).lowStockThreshold(10).build()).block();
        assertNotNull(product1);

        SalesInvoice inv = SalesInvoice.builder()
            .customerId(customer.getId())
            .invoiceDate(LocalDateTime.now())
            .saleType(SaleType.INSTALLMENT)
            .subTotalAmount(1200.0)
            .additionalServicesTotalAmount(0.0)
            .totalAmount(1200.0)
            .paymentStatus(PaymentStatus.PENDING_CASH) // Or PENDING_INSTALLMENT if SalesService handles this transition immediately
            .build();
        salesInvoiceInstallment = salesInvoiceRepository.save(inv).block();
        assertNotNull(salesInvoiceInstallment);

        InvoiceItem item = InvoiceItem.builder()
            .salesInvoiceId(salesInvoiceInstallment.getId())
            .productId(product1.getId())
            .productName(product1.getName())
            .quantity(1) // Assuming SalesService already reduced stock for this.
            .unitPrice(product1.getPrice())
            .subtotal(product1.getPrice())
            .build();
        invoiceItemRepository.save(item).block();
    }

    @Test
    @Order(1)
    void testCreateInstallmentPlan_Success() {
        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(
            salesInvoiceInstallment.getId(),
            12, // numberOfInstallments
            PaymentInterval.MONTHLY,
            null, // customIntervalDays
            LocalDate.now().plusDays(1) // startDate
        );

        webTestClient.post().uri("/api/v1/installment-plans")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Mono.just(request), CreateInstallmentPlanRequest.class)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(InstallmentPlan.class)
            .value(plan -> {
                assertNotNull(plan.getId());
                assertEquals(salesInvoiceInstallment.getId(), plan.getSalesInvoiceId());
                assertEquals(12, plan.getNumberOfInstallments());
                assertEquals(100.0, plan.getInstallmentAmount(), 0.01); // 1200 / 12
                assertEquals(InstallmentPlanStatus.ACTIVE, plan.getStatus());
                assertNotNull(plan.getPayments());
                assertEquals(12, plan.getPayments().size());
                plan.getPayments().forEach(p -> assertEquals(PaymentStatus.PENDING_INSTALLMENT, p.getPaymentStatus()));
            });

        // Verify SalesInvoice status update
        SalesInvoice updatedInvoice = salesInvoiceRepository.findById(salesInvoiceInstallment.getId()).block();
        assertNotNull(updatedInvoice);
        assertEquals(PaymentStatus.PENDING_INSTALLMENT, updatedInvoice.getPaymentStatus());
    }

    @Test
    @Order(2)
    void testCreateInstallmentPlan_Fail_InvoiceNotFound() {
        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(9999L, 12, PaymentInterval.MONTHLY, null, LocalDate.now());
        webTestClient.post().uri("/api/v1/installment-plans")
            .body(Mono.just(request), CreateInstallmentPlanRequest.class)
            .exchange()
            .expectStatus().is5xxServerError(); // Or map to 404 in controller advice
    }
    
    @Test
    @Order(3)
    void testCreateInstallmentPlan_Fail_NotInstallmentType() {
        SalesInvoice cashInvoice = salesInvoiceRepository.save(SalesInvoice.builder()
            .customerId(customer.getId()).invoiceDate(LocalDateTime.now()).saleType(SaleType.CASH)
            .totalAmount(100.0).subTotalAmount(100.0).additionalServicesTotalAmount(0.0)
            .paymentStatus(PaymentStatus.PAID).build()).block();
        assertNotNull(cashInvoice);

        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(cashInvoice.getId(), 12, PaymentInterval.MONTHLY, null, LocalDate.now());
        webTestClient.post().uri("/api/v1/installment-plans")
            .body(Mono.just(request), CreateInstallmentPlanRequest.class)
            .exchange()
            .expectStatus().is5xxServerError(); // Or map to 400
    }

    @Test
    @Order(4)
    void testCreateInstallmentPlan_Fail_PlanAlreadyExists() {
        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(salesInvoiceInstallment.getId(), 12, PaymentInterval.MONTHLY, null, LocalDate.now());
        // Create one plan successfully
        webTestClient.post().uri("/api/v1/installment-plans").body(Mono.just(request), CreateInstallmentPlanRequest.class).exchange().expectStatus().isCreated();
        // Attempt to create another for the same invoice
        webTestClient.post().uri("/api/v1/installment-plans").body(Mono.just(request), CreateInstallmentPlanRequest.class).exchange().expectStatus().is5xxServerError(); // Or map to 409
    }

    // Helper to create a plan for payment tests
    private InstallmentPlan createPlanForPaymentTests(int numberOfInstallments, double totalAmount) {
         // Update salesInvoiceInstallment for this specific test's total amount
        salesInvoiceInstallment.setTotalAmount(totalAmount);
        salesInvoiceInstallment.setSubTotalAmount(totalAmount); // Assuming no additional services for simplicity here
        salesInvoiceInstallment = salesInvoiceRepository.save(salesInvoiceInstallment).block();


        CreateInstallmentPlanRequest planRequest = new CreateInstallmentPlanRequest(
            salesInvoiceInstallment.getId(), numberOfInstallments, PaymentInterval.MONTHLY, null, LocalDate.now().plusDays(1)
        );
        return webTestClient.post().uri("/api/v1/installment-plans")
            .body(Mono.just(planRequest), CreateInstallmentPlanRequest.class)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(InstallmentPlan.class)
            .returnResult().getResponseBody();
    }

    @Test
    @Order(5)
    void testRecordPayment_Success_PlanNotCompleted() {
        InstallmentPlan plan = createPlanForPaymentTests(3, 300.0); // 3 payments of 100 each
        assertNotNull(plan);
        assertNotNull(plan.getPayments());
        assertFalse(plan.getPayments().isEmpty());
        InstallmentPayment firstPayment = plan.getPayments().get(0);

        RecordPaymentRequest paymentRecord = new RecordPaymentRequest(firstPayment.getAmountDue(), LocalDate.now());

        webTestClient.post().uri("/api/v1/installment-payments/" + firstPayment.getId() + "/pay")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Mono.just(paymentRecord), RecordPaymentRequest.class)
            .exchange()
            .expectStatus().isOk()
            .expectBody(InstallmentPayment.class)
            .value(p -> {
                assertEquals(PaymentStatus.PAID, p.getPaymentStatus());
                assertEquals(firstPayment.getAmountDue(), p.getAmountPaid());
                assertNotNull(p.getPaymentDate());
            });

        // Verify plan and invoice status
        InstallmentPlan updatedPlan = installmentPlanRepository.findById(plan.getId()).block();
        assertNotNull(updatedPlan);
        assertEquals(InstallmentPlanStatus.ACTIVE, updatedPlan.getStatus());
        SalesInvoice updatedInvoice = salesInvoiceRepository.findById(salesInvoiceInstallment.getId()).block();
        assertNotNull(updatedInvoice);
        assertEquals(PaymentStatus.PENDING_INSTALLMENT, updatedInvoice.getPaymentStatus());
    }

    @Test
    @Order(6)
    void testRecordPayment_Success_FinalPayment_PlanCompleted() {
        InstallmentPlan plan = createPlanForPaymentTests(1, 100.0); // 1 payment of 100
        InstallmentPayment theOnlyPayment = plan.getPayments().get(0);

        RecordPaymentRequest paymentRecord = new RecordPaymentRequest(theOnlyPayment.getAmountDue(), LocalDate.now());

        webTestClient.post().uri("/api/v1/installment-payments/" + theOnlyPayment.getId() + "/pay")
            .body(Mono.just(paymentRecord), RecordPaymentRequest.class)
            .exchange()
            .expectStatus().isOk();

        // Verify statuses
        InstallmentPlan completedPlan = installmentPlanRepository.findById(plan.getId()).block();
        assertNotNull(completedPlan);
        assertEquals(InstallmentPlanStatus.COMPLETED, completedPlan.getStatus());

        SalesInvoice paidInvoice = salesInvoiceRepository.findById(salesInvoiceInstallment.getId()).block();
        assertNotNull(paidInvoice);
        assertEquals(PaymentStatus.PAID, paidInvoice.getPaymentStatus());
    }
    
    @Test
    @Order(7)
    void testRecordPayment_Fail_PaymentNotFound() {
        RecordPaymentRequest paymentRecord = new RecordPaymentRequest(100.0, LocalDate.now());
        webTestClient.post().uri("/api/v1/installment-payments/99999/pay")
            .body(Mono.just(paymentRecord), RecordPaymentRequest.class)
            .exchange()
            .expectStatus().is5xxServerError(); // Or map to 404
    }

    @Test
    @Order(8)
    void testGetInstallmentPlanBySalesInvoiceId_Success() {
        InstallmentPlan plan = createPlanForPaymentTests(2, 200.0);
        webTestClient.get().uri("/api/v1/sales-invoices/" + salesInvoiceInstallment.getId() + "/installment-plan")
            .exchange()
            .expectStatus().isOk()
            .expectBody(InstallmentPlan.class)
            .value(fetchedPlan -> {
                assertEquals(plan.getId(), fetchedPlan.getId());
                assertNotNull(fetchedPlan.getPayments());
                assertEquals(2, fetchedPlan.getPayments().size());
            });
    }

    @Test
    @Order(9)
    void testGetInstallmentPlanBySalesInvoiceId_NotFound() {
        webTestClient.get().uri("/api/v1/sales-invoices/9999/installment-plan")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    @Order(10)
    void testGetPaymentsForPlan_Success() {
        InstallmentPlan plan = createPlanForPaymentTests(3, 300.0);
        webTestClient.get().uri("/api/v1/installment-plans/" + plan.getId() + "/payments")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(InstallmentPayment.class).hasSize(3);
    }

    @Test
    @Order(11)
    void testGetInstallmentPlanWithPayments_Success() {
        InstallmentPlan plan = createPlanForPaymentTests(2, 200.0);
        webTestClient.get().uri("/api/v1/installment-plans/" + plan.getId())
            .exchange()
            .expectStatus().isOk()
            .expectBody(InstallmentPlan.class)
            .value(fetchedPlan -> {
                assertEquals(plan.getId(), fetchedPlan.getId());
                assertNotNull(fetchedPlan.getPayments());
                assertEquals(2, fetchedPlan.getPayments().size());
            });
    }
}
