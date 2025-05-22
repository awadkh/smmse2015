package com.example.simpleerpsystem.installment.repository;

import com.example.simpleerpsystem.installment.entity.InstallmentPlan;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface InstallmentPlanRepository extends ReactiveCrudRepository<InstallmentPlan, Long> {
    Mono<InstallmentPlan> findBySalesInvoiceId(Long salesInvoiceId);
}
