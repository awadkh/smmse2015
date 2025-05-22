package com.example.simpleerpsystem.installment.controller;

import com.example.simpleerpsystem.installment.dto.CreateInstallmentPlanRequest;
import com.example.simpleerpsystem.installment.dto.RecordPaymentRequest;
import com.example.simpleerpsystem.installment.entity.InstallmentPayment;
import com.example.simpleerpsystem.installment.entity.InstallmentPlan;
import com.example.simpleerpsystem.installment.service.InstallmentService;
import lombok.RequiredArgsConstructor;
// import jakarta.validation.Valid; // For future use
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1") // Base path for this controller
@RequiredArgsConstructor
public class InstallmentController {

    private final InstallmentService installmentService;

    @PostMapping("/installment-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<InstallmentPlan> createInstallmentPlan(@RequestBody /*@Valid*/ CreateInstallmentPlanRequest request) {
        return installmentService.createInstallmentPlan(request);
    }

    @PostMapping("/installment-payments/{paymentId}/pay")
    public Mono<InstallmentPayment> recordPayment(@PathVariable Long paymentId, @RequestBody /*@Valid*/ RecordPaymentRequest request) {
        return installmentService.recordPayment(paymentId, request.amountPaid(), request.paymentDate());
    }

    @GetMapping("/sales-invoices/{invoiceId}/installment-plan")
    public Mono<ResponseEntity<InstallmentPlan>> getInstallmentPlanBySalesInvoiceId(@PathVariable Long invoiceId) {
        return installmentService.getInstallmentPlanBySalesInvoiceId(invoiceId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/installment-plans/{planId}/payments")
    public Flux<InstallmentPayment> getPaymentsForPlan(@PathVariable Long planId) {
        return installmentService.getPaymentsForPlan(planId);
    }

    @GetMapping("/installment-plans/{planId}")
    public Mono<ResponseEntity<InstallmentPlan>> getInstallmentPlanWithPayments(@PathVariable Long planId) {
        return installmentService.getInstallmentPlanWithPayments(planId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
