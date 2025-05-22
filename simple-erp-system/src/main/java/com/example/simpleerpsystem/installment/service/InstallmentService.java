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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InstallmentService {

    private final InstallmentPlanRepository installmentPlanRepository;
    private final InstallmentPaymentRepository installmentPaymentRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;

    private LocalDate calculateNextDueDate(LocalDate previousDueDate, PaymentInterval interval, Integer customDays) {
        return switch (interval) {
            case MONTHLY -> previousDueDate.plusMonths(1);
            case QUARTERLY -> previousDueDate.plusMonths(3);
            case ANNUALLY -> previousDueDate.plusYears(1);
            case CUSTOM_DAYS -> {
                if (customDays == null || customDays <= 0) {
                    throw new IllegalArgumentException("Custom interval days must be positive for CUSTOM_DAYS interval.");
                }
                yield previousDueDate.plusDays(customDays);
            }
        };
    }

    public Mono<InstallmentPlan> createInstallmentPlan(CreateInstallmentPlanRequest request) {
        return salesInvoiceRepository.findById(request.salesInvoiceId())
            .switchIfEmpty(Mono.error(new RuntimeException("SalesInvoice not found with ID: " + request.salesInvoiceId())))
            .flatMap(salesInvoice -> {
                if (salesInvoice.getSaleType() != SaleType.INSTALLMENT) {
                    return Mono.error(new RuntimeException("Installment plan can only be created for INSTALLMENT sale type."));
                }
                if (salesInvoice.getPaymentStatus() == PaymentStatus.PAID) {
                    return Mono.error(new RuntimeException("Installment plan cannot be created for an already PAID invoice."));
                }

                return installmentPlanRepository.findBySalesInvoiceId(request.salesInvoiceId())
                    .flatMap(existingPlan -> Mono.error(new RuntimeException("Installment plan already exists for SalesInvoice ID: " + request.salesInvoiceId())))
                    .switchIfEmpty(Mono.defer(() -> { // Proceed if no existing plan
                        double totalAmount = salesInvoice.getTotalAmount();
                        double installmentAmount = Math.round((totalAmount / request.numberOfInstallments()) * 100.0) / 100.0; // Round to 2 decimal places

                        LocalDate endDate = request.startDate();
                        if (request.numberOfInstallments() > 0) {
                            LocalDate tempDate = request.startDate();
                            for (int i = 0; i < request.numberOfInstallments() -1; i++) { // -1 because first payment is on startDate, subsequent are next due dates
                                tempDate = calculateNextDueDate(tempDate, request.paymentInterval(), request.customIntervalDays());
                            }
                             // The end_date should be the due date of the last installment
                            if(request.numberOfInstallments() > 1) { // If more than 1 installment, calculate for the last one
                                endDate = calculateNextDueDate(request.startDate(), request.paymentInterval(), request.customIntervalDays());
                                for (int i = 1; i < request.numberOfInstallments() -1; i++) {
                                     endDate = calculateNextDueDate(endDate, request.paymentInterval(), request.customIntervalDays());
                                }
                            } else { // if only one installment, end_date is start_date
                                endDate = request.startDate();
                            }
                        }


                        InstallmentPlan plan = InstallmentPlan.builder()
                            .salesInvoiceId(request.salesInvoiceId())
                            .totalInstallmentAmount(totalAmount)
                            .numberOfInstallments(request.numberOfInstallments())
                            .installmentAmount(installmentAmount)
                            .paymentInterval(request.paymentInterval())
                            .customIntervalDays(request.customIntervalDays())
                            .startDate(request.startDate())
                            .endDate(endDate) // This endDate calculation needs careful review
                            .status(InstallmentPlanStatus.ACTIVE)
                            .build();

                        return installmentPlanRepository.save(plan)
                            .flatMap(savedPlan -> {
                                List<InstallmentPayment> payments = new ArrayList<>();
                                LocalDate currentDueDate = request.startDate();
                                for (int i = 0; i < request.numberOfInstallments(); i++) {
                                    payments.add(InstallmentPayment.builder()
                                        .installmentPlanId(savedPlan.getId())
                                        .dueDate(currentDueDate)
                                        .amountDue(installmentAmount)
                                        .paymentStatus(PaymentStatus.PENDING_INSTALLMENT) // Or PENDING for general case
                                        .build());
                                    if (i < request.numberOfInstallments() - 1) { // Don't calculate next due date for the last payment
                                        currentDueDate = calculateNextDueDate(currentDueDate, request.paymentInterval(), request.customIntervalDays());
                                    }
                                }
                                return installmentPaymentRepository.saveAll(payments).collectList()
                                    .flatMap(savedPayments -> {
                                        savedPlan.setPayments(savedPayments);
                                        salesInvoice.setPaymentStatus(PaymentStatus.PENDING_INSTALLMENT);
                                        return salesInvoiceRepository.save(salesInvoice).thenReturn(savedPlan);
                                    });
                            });
                    })).cast(InstallmentPlan.class); // Cast needed because of switchIfEmpty structure
            });
    }


    public Mono<InstallmentPayment> recordPayment(Long installmentPaymentId, Double amountPaid, LocalDate paymentDate) {
        return installmentPaymentRepository.findById(installmentPaymentId)
            .switchIfEmpty(Mono.error(new RuntimeException("InstallmentPayment not found with ID: " + installmentPaymentId)))
            .flatMap(payment -> {
                if (amountPaid < payment.getAmountDue()) {
                    // For V1, simplified. V2 might handle partial payments.
                    return Mono.error(new RuntimeException("Amount paid (" + amountPaid + ") is less than amount due (" + payment.getAmountDue() + ")."));
                }
                payment.setAmountPaid(amountPaid);
                payment.setPaymentDate(paymentDate);
                payment.setPaymentStatus(PaymentStatus.PAID);
                return installmentPaymentRepository.save(payment);
            })
            .flatMap(savedPayment ->
                installmentPaymentRepository.findByInstallmentPlanIdOrderByDueDateAsc(savedPayment.getInstallmentPlanId())
                    .collectList()
                    .flatMap(allPaymentsForPlan -> {
                        boolean allPaid = allPaymentsForPlan.stream().allMatch(p -> p.getPaymentStatus() == PaymentStatus.PAID);
                        if (allPaid) {
                            return installmentPlanRepository.findById(savedPayment.getInstallmentPlanId())
                                .flatMap(plan -> {
                                    plan.setStatus(InstallmentPlanStatus.COMPLETED);
                                    return installmentPlanRepository.save(plan)
                                        .flatMap(updatedPlan -> salesInvoiceRepository.findById(updatedPlan.getSalesInvoiceId())
                                            .flatMap(invoice -> {
                                                invoice.setPaymentStatus(PaymentStatus.PAID);
                                                return salesInvoiceRepository.save(invoice).thenReturn(savedPayment);
                                            }));
                                });
                        }
                        return Mono.just(savedPayment);
                    })
            );
    }

    public Mono<InstallmentPlan> getInstallmentPlanWithPayments(Long installmentPlanId) {
        return installmentPlanRepository.findById(installmentPlanId)
            .flatMap(plan -> installmentPaymentRepository.findByInstallmentPlanIdOrderByDueDateAsc(installmentPlanId)
                .collectList()
                .map(payments -> {
                    plan.setPayments(payments);
                    return plan;
                })
            );
    }

    public Flux<InstallmentPayment> getPaymentsForPlan(Long installmentPlanId) {
        return installmentPaymentRepository.findByInstallmentPlanIdOrderByDueDateAsc(installmentPlanId);
    }

    public Mono<InstallmentPlan> getInstallmentPlanBySalesInvoiceId(Long salesInvoiceId) {
        return installmentPlanRepository.findBySalesInvoiceId(salesInvoiceId)
            .flatMap(plan -> {
                if (plan == null) return Mono.empty(); // Should be handled by findBySalesInvoiceId returning Mono.empty()
                return getInstallmentPlanWithPayments(plan.getId());
            });
    }
}
