package org.example.paymentservice.purchase.repository;

import org.example.paymentservice.purchase.entity.RollBackFailure;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RollBackFailureRepository extends ReactiveCrudRepository<RollBackFailure, Long> {
}
