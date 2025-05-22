package com.example.simpleerpsystem.installment.repository;

import com.example.simpleerpsystem.installment.entity.InstallmentPayment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface InstallmentPaymentRepository extends ReactiveCrudRepository<InstallmentPayment, Long> {
    Flux<InstallmentPayment> findByInstallmentPlanIdOrderByDueDateAsc(Long installmentPlanId);
}
