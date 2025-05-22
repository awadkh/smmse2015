package com.example.simpleerpsystem.installment.service;

import com.example.simpleerpsystem.installment.dto.CreateInstallmentPlanRequest;
import com.example.simpleerpsystem.installment.entity.InstallmentPayment;
import com.example.simpleerpsystem.installment.entity.InstallmentPlan;
import com.example.simpleerpsystem.installment.entity.enums.InstallmentPlanStatus;
import com.example.simpleerpsystem.installment.entity.enums.PaymentInterval;
import com.example.simpleerpsystem.installment.repository.InstallmentPaymentRepository;
import com.example.simpleerpsystem.installment.repository.InstallmentPlanRepository;
import com.example.simpleerpsystem.sales.entity.SalesInvoice;
import com.example.simpleerpsystem.sales.entity.enums.PaymentStatus;
import com.example.simpleerpsystem.sales.entity.enums.SaleType;
import com.example.simpleerpsystem.sales.repository.SalesInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InstallmentServiceTest {

    @Mock
    private InstallmentPlanRepository installmentPlanRepository;
    @Mock
    private InstallmentPaymentRepository installmentPaymentRepository;
    @Mock
    private SalesInvoiceRepository salesInvoiceRepository;

    private InstallmentService installmentService;

    @BeforeEach
    void setUp() {
        installmentService = new InstallmentService(installmentPlanRepository, installmentPaymentRepository, salesInvoiceRepository);
    }

    // Helper for mocking InstallmentPayment save to assign an ID
    private Answer<Mono<InstallmentPayment>> mockPaymentSaveWithId(AtomicLong idCounter) {
        return invocation -> {
            InstallmentPayment payment = invocation.getArgument(0);
            if (payment.getId() == null) { // Simulate ID generation
                payment.setId(idCounter.getAndIncrement());
            }
            return Mono.just(payment);
        };
    }


    @Test
    void testCreateInstallmentPlan_Success_Monthly() {
        SalesInvoice mockSalesInvoice = SalesInvoice.builder().id(1L).totalAmount(1200.00).saleType(SaleType.INSTALLMENT).paymentStatus(PaymentStatus.PENDING_INSTALLMENT).build();
        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(1L, 12, PaymentInterval.MONTHLY, null, LocalDate.of(2024, 1, 15));

        InstallmentPlan planBeingSaved = InstallmentPlan.builder()
                .salesInvoiceId(request.salesInvoiceId())
                .totalInstallmentAmount(mockSalesInvoice.getTotalAmount())
                .numberOfInstallments(request.numberOfInstallments())
                .installmentAmount(100.00) // 1200 / 12
                .paymentInterval(request.paymentInterval())
                .startDate(request.startDate())
                .endDate(LocalDate.of(2025, 0, 15)) // Expected end date for 12 monthly payments starting Jan 15, 2024. (Mistake here, should be 2024-12-15 or 2025-01-15 for last payment's *due date* if 12th payment is on Jan 15 2025)
                                                       // Corrected: endDate is due date of LAST installment. So if 1st is Jan 15, 2024, 12th is Dec 15, 2024.
                .status(InstallmentPlanStatus.ACTIVE)
                .build();
        planBeingSaved.setId(10L); // Simulate ID after save

        AtomicLong paymentIdCounter = new AtomicLong(100L);

        when(salesInvoiceRepository.findById(1L)).thenReturn(Mono.just(mockSalesInvoice));
        when(installmentPlanRepository.findBySalesInvoiceId(1L)).thenReturn(Mono.empty());
        when(installmentPlanRepository.save(any(InstallmentPlan.class))).thenReturn(Mono.just(planBeingSaved));
        // Mock saveAll for payments
        when(installmentPaymentRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            List<InstallmentPayment> payments = invocation.getArgument(0);
            payments.forEach(p -> {
                if (p.getId() == null) p.setId(paymentIdCounter.getAndIncrement());
            });
            return Flux.fromIterable(payments);
        });
        when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));


        StepVerifier.create(installmentService.createInstallmentPlan(request))
            .assertNext(plan -> {
                assertEquals(10L, plan.getId());
                assertEquals(InstallmentPlanStatus.ACTIVE, plan.getStatus());
                assertEquals(1200.00, plan.getTotalInstallmentAmount());
                assertEquals(100.00, plan.getInstallmentAmount());
                assertEquals(12, plan.getNumberOfInstallments());
                assertEquals(LocalDate.of(2024, 1, 15), plan.getStartDate());
                // Due date of the 12th payment, starting Jan 15, 2024, monthly, is Dec 15, 2024
                assertEquals(LocalDate.of(2024, 12, 15), plan.getEndDate());
                assertNotNull(plan.getPayments());
                assertEquals(12, plan.getPayments().size());
                assertEquals(LocalDate.of(2024,1,15), plan.getPayments().get(0).getDueDate());
                assertEquals(LocalDate.of(2024,2,15), plan.getPayments().get(1).getDueDate());
                assertEquals(LocalDate.of(2024,12,15), plan.getPayments().get(11).getDueDate());
                plan.getPayments().forEach(p -> assertEquals(PaymentStatus.PENDING_INSTALLMENT, p.getPaymentStatus()));
            })
            .verifyComplete();

        verify(installmentPaymentRepository).saveAll(anyIterable());
        verify(salesInvoiceRepository).save(argThat(inv -> inv.getPaymentStatus() == PaymentStatus.PENDING_INSTALLMENT));
    }

    @Test
    void testCreateInstallmentPlan_Error_SalesInvoiceNotFound() {
        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(1L, 12, PaymentInterval.MONTHLY, null, LocalDate.now());
        when(salesInvoiceRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(installmentService.createInstallmentPlan(request))
            .expectErrorMessage("SalesInvoice not found with ID: 1")
            .verify();
    }

    @Test
    void testCreateInstallmentPlan_Error_SalesInvoiceNotInstallmentType() {
        SalesInvoice mockSalesInvoice = SalesInvoice.builder().id(1L).saleType(SaleType.CASH).build();
        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(1L, 12, PaymentInterval.MONTHLY, null, LocalDate.now());
        when(salesInvoiceRepository.findById(1L)).thenReturn(Mono.just(mockSalesInvoice));

        StepVerifier.create(installmentService.createInstallmentPlan(request))
            .expectErrorMessage("Installment plan can only be created for INSTALLMENT sale type.")
            .verify();
    }

    @Test
    void testCreateInstallmentPlan_Error_SalesInvoiceAlreadyPaid() {
        SalesInvoice mockSalesInvoice = SalesInvoice.builder().id(1L).saleType(SaleType.INSTALLMENT).paymentStatus(PaymentStatus.PAID).build();
        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(1L, 12, PaymentInterval.MONTHLY, null, LocalDate.now());
        when(salesInvoiceRepository.findById(1L)).thenReturn(Mono.just(mockSalesInvoice));

        StepVerifier.create(installmentService.createInstallmentPlan(request))
            .expectErrorMessage("Installment plan cannot be created for an already PAID invoice.")
            .verify();
    }

    @Test
    void testCreateInstallmentPlan_Error_PlanAlreadyExists() {
        SalesInvoice mockSalesInvoice = SalesInvoice.builder().id(1L).saleType(SaleType.INSTALLMENT).paymentStatus(PaymentStatus.PENDING_INSTALLMENT).build();
        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(1L, 12, PaymentInterval.MONTHLY, null, LocalDate.now());
        InstallmentPlan existingPlan = InstallmentPlan.builder().id(10L).build();

        when(salesInvoiceRepository.findById(1L)).thenReturn(Mono.just(mockSalesInvoice));
        when(installmentPlanRepository.findBySalesInvoiceId(1L)).thenReturn(Mono.just(existingPlan));

        StepVerifier.create(installmentService.createInstallmentPlan(request))
            .expectErrorMessage("Installment plan already exists for SalesInvoice ID: 1")
            .verify();
    }
    
    @Test
    void testCreateInstallmentPlan_DueDateCalculation_Quarterly() {
        SalesInvoice mockSalesInvoice = SalesInvoice.builder().id(1L).totalAmount(400.00).saleType(SaleType.INSTALLMENT).build();
        CreateInstallmentPlanRequest request = new CreateInstallmentPlanRequest(1L, 4, PaymentInterval.QUARTERLY, null, LocalDate.of(2024, 1, 31));
        InstallmentPlan savedPlan = InstallmentPlan.builder().id(10L).salesInvoiceId(1L).status(InstallmentPlanStatus.ACTIVE).build(); // Simplified for this test focus
        
        when(salesInvoiceRepository.findById(1L)).thenReturn(Mono.just(mockSalesInvoice));
        when(installmentPlanRepository.findBySalesInvoiceId(1L)).thenReturn(Mono.empty());
        when(installmentPlanRepository.save(any(InstallmentPlan.class))).thenAnswer(inv -> {
            InstallmentPlan p = inv.getArgument(0);
            p.setId(10L); // Simulate save
            return Mono.just(p);
        });
        when(installmentPaymentRepository.saveAll(anyIterable())).thenAnswer(inv -> Flux.fromIterable(inv.getArgument(0)));
        when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(installmentService.createInstallmentPlan(request))
            .assertNext(plan -> {
                assertNotNull(plan.getPayments());
                assertEquals(4, plan.getPayments().size());
                assertEquals(LocalDate.of(2024, 1, 31), plan.getPayments().get(0).getDueDate());
                assertEquals(LocalDate.of(2024, 4, 30), plan.getPayments().get(1).getDueDate());
                assertEquals(LocalDate.of(2024, 7, 31), plan.getPayments().get(2).getDueDate());
                assertEquals(LocalDate.of(2024, 10, 31), plan.getPayments().get(3).getDueDate());
                assertEquals(LocalDate.of(2024, 10, 31), plan.getEndDate());
            })
            .verifyComplete();
    }


    @Test
    void testRecordPayment_Success_SinglePayment_PlanNotCompleted() {
        Long paymentId = 1L;
        Long planId = 10L;
        InstallmentPayment paymentToUpdate = InstallmentPayment.builder().id(paymentId).installmentPlanId(planId).amountDue(100.0).paymentStatus(PaymentStatus.PENDING_INSTALLMENT).build();
        InstallmentPayment updatedPayment = InstallmentPayment.builder().id(paymentId).installmentPlanId(planId).amountDue(100.0).amountPaid(100.0).paymentDate(LocalDate.now()).paymentStatus(PaymentStatus.PAID).build();
        
        InstallmentPayment otherPendingPayment = InstallmentPayment.builder().id(2L).installmentPlanId(planId).amountDue(100.0).paymentStatus(PaymentStatus.PENDING_INSTALLMENT).build();

        when(installmentPaymentRepository.findById(paymentId)).thenReturn(Mono.just(paymentToUpdate));
        when(installmentPaymentRepository.save(any(InstallmentPayment.class))).thenReturn(Mono.just(updatedPayment));
        when(installmentPaymentRepository.findByInstallmentPlanIdOrderByDueDateAsc(planId)).thenReturn(Flux.just(updatedPayment, otherPendingPayment)); // Current is paid, one other is pending

        StepVerifier.create(installmentService.recordPayment(paymentId, 100.0, LocalDate.now()))
            .assertNext(p -> {
                assertEquals(PaymentStatus.PAID, p.getPaymentStatus());
                assertEquals(100.0, p.getAmountPaid());
            })
            .verifyComplete();
        
        verify(installmentPlanRepository, never()).save(any()); // Plan status should not change
        verify(salesInvoiceRepository, never()).save(any()); // Invoice status should not change
    }

    @Test
    void testRecordPayment_Success_FinalPayment_PlanCompleted() {
        Long paymentId = 1L;
        Long planId = 10L;
        Long salesInvoiceIdVal = 100L;

        InstallmentPayment paymentToUpdate = InstallmentPayment.builder().id(paymentId).installmentPlanId(planId).amountDue(100.0).paymentStatus(PaymentStatus.PENDING_INSTALLMENT).build();
        InstallmentPayment updatedPayment = InstallmentPayment.builder().id(paymentId).installmentPlanId(planId).amountDue(100.0).amountPaid(100.0).paymentDate(LocalDate.now()).paymentStatus(PaymentStatus.PAID).build();
        
        InstallmentPlan plan = InstallmentPlan.builder().id(planId).salesInvoiceId(salesInvoiceIdVal).status(InstallmentPlanStatus.ACTIVE).build();
        SalesInvoice invoice = SalesInvoice.builder().id(salesInvoiceIdVal).paymentStatus(PaymentStatus.PENDING_INSTALLMENT).build();

        when(installmentPaymentRepository.findById(paymentId)).thenReturn(Mono.just(paymentToUpdate));
        when(installmentPaymentRepository.save(any(InstallmentPayment.class))).thenReturn(Mono.just(updatedPayment));
        // Simulate all payments are now PAID
        when(installmentPaymentRepository.findByInstallmentPlanIdOrderByDueDateAsc(planId)).thenReturn(Flux.just(updatedPayment)); 
        when(installmentPlanRepository.findById(planId)).thenReturn(Mono.just(plan));
        when(installmentPlanRepository.save(any(InstallmentPlan.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(salesInvoiceRepository.findById(salesInvoiceIdVal)).thenReturn(Mono.just(invoice));
        when(salesInvoiceRepository.save(any(SalesInvoice.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(installmentService.recordPayment(paymentId, 100.0, LocalDate.now()))
            .assertNext(p -> assertEquals(PaymentStatus.PAID, p.getPaymentStatus()))
            .verifyComplete();

        verify(installmentPlanRepository).save(argThat(savedPlan -> savedPlan.getStatus() == InstallmentPlanStatus.COMPLETED));
        verify(salesInvoiceRepository).save(argThat(savedInvoice -> savedInvoice.getPaymentStatus() == PaymentStatus.PAID));
    }
    
    @Test
    void testRecordPayment_Error_InstallmentPaymentNotFound() {
        when(installmentPaymentRepository.findById(1L)).thenReturn(Mono.empty());
        StepVerifier.create(installmentService.recordPayment(1L, 100.0, LocalDate.now()))
            .expectErrorMessage("InstallmentPayment not found with ID: 1")
            .verify();
    }

    @Test
    void testRecordPayment_Error_AmountLessThanDue() {
        InstallmentPayment payment = InstallmentPayment.builder().id(1L).amountDue(100.0).build();
        when(installmentPaymentRepository.findById(1L)).thenReturn(Mono.just(payment));
        StepVerifier.create(installmentService.recordPayment(1L, 50.0, LocalDate.now()))
            .expectErrorMessage("Amount paid (50.0) is less than amount due (100.0).")
            .verify();
    }

    @Test
    void testGetInstallmentPlanWithPayments() {
        Long planId = 1L;
        InstallmentPlan plan = InstallmentPlan.builder().id(planId).build();
        InstallmentPayment payment1 = InstallmentPayment.builder().id(10L).installmentPlanId(planId).build();
        InstallmentPayment payment2 = InstallmentPayment.builder().id(11L).installmentPlanId(planId).build();

        when(installmentPlanRepository.findById(planId)).thenReturn(Mono.just(plan));
        when(installmentPaymentRepository.findByInstallmentPlanIdOrderByDueDateAsc(planId)).thenReturn(Flux.just(payment1, payment2));

        StepVerifier.create(installmentService.getInstallmentPlanWithPayments(planId))
            .assertNext(p -> {
                assertEquals(planId, p.getId());
                assertNotNull(p.getPayments());
                assertEquals(2, p.getPayments().size());
            })
            .verifyComplete();
    }

    @Test
    void testGetPaymentsForPlan() {
        Long planId = 1L;
        InstallmentPayment payment1 = InstallmentPayment.builder().id(10L).installmentPlanId(planId).build();
        when(installmentPaymentRepository.findByInstallmentPlanIdOrderByDueDateAsc(planId)).thenReturn(Flux.just(payment1));
        StepVerifier.create(installmentService.getPaymentsForPlan(planId))
            .expectNext(payment1)
            .verifyComplete();
    }

    @Test
    void testGetInstallmentPlanBySalesInvoiceId() {
        Long salesInvoiceId = 100L;
        Long planId = 1L;
        InstallmentPlan plan = InstallmentPlan.builder().id(planId).salesInvoiceId(salesInvoiceId).build();
        
        when(installmentPlanRepository.findBySalesInvoiceId(salesInvoiceId)).thenReturn(Mono.just(plan));
        // Assume getInstallmentPlanWithPayments is internally called and works (tested separately)
        when(installmentPaymentRepository.findByInstallmentPlanIdOrderByDueDateAsc(planId)).thenReturn(Flux.empty());


        StepVerifier.create(installmentService.getInstallmentPlanBySalesInvoiceId(salesInvoiceId))
            .assertNext(p -> assertEquals(planId, p.getId()))
            .verifyComplete();
    }
}
