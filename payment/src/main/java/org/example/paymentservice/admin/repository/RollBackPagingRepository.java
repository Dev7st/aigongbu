package org.example.paymentservice.admin.repository;

import org.example.paymentservice.purchase.entity.RollBackFailure;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import reactor.core.publisher.Flux;

public interface RollBackPagingRepository extends ReactiveSortingRepository<RollBackFailure, Long> {
    Flux<RollBackFailure> findAll();
}
