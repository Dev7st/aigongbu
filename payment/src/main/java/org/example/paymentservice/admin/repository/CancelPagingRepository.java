package org.example.paymentservice.admin.repository;

import org.example.paymentservice.purchase.entity.CancelFailure;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import reactor.core.publisher.Flux;

public interface CancelPagingRepository extends ReactiveSortingRepository<CancelFailure, Long> {
    Flux<CancelFailure> findAll();
}
