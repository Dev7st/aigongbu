package org.example.paymentservice.purchase.repository;

import org.example.paymentservice.purchase.entity.CancelFailure;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface CancelFailureRepository extends ReactiveCrudRepository<CancelFailure, Long> {
    Mono<CancelFailure> findByPurchaseId(Long purchaseId);
}
