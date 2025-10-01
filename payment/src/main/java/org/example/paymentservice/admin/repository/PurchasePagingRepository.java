package org.example.paymentservice.admin.repository;

import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.type.Status;
import org.springframework.data.repository.reactive.ReactiveSortingRepository;
import reactor.core.publisher.Flux;

public interface PurchasePagingRepository extends ReactiveSortingRepository<Purchase, Long> {
    Flux<Purchase> findAllByStatus(Status status);
}
