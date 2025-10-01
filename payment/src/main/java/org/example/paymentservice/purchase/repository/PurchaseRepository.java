package org.example.paymentservice.purchase.repository;

import org.example.paymentservice.purchase.entity.Purchase;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface PurchaseRepository extends ReactiveCrudRepository<Purchase, Long> {

    Mono<Purchase> findByMerchantUid(String merchantUid); // Spring Data R2DBC가 자동으로 쿼리 만들어줌 (조건: DB에 merchant_uid 컬럼 있어야 함)

    Flux<Purchase> findAllByUserId(Long userId);

    Mono<Boolean> existsByImpUid(String impUid);
}
