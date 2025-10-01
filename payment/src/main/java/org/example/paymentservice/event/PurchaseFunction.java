package org.example.paymentservice.event;

import org.example.paymentservice.saga.PurchaseSagaCoordinator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

@Configuration
public class PurchaseFunction {

    @Bean
    public Consumer<Flux<VerifiedMessage>> verified(PurchaseSagaCoordinator saga) {
        return flux -> saga.consumerVerifiedEvent(flux)
                .subscribe();
    }

    @Bean
    public Consumer<Flux<RollBackedMessage>> rollBacked(PurchaseSagaCoordinator saga) {
        return flux -> saga.consumerRollBackedEvent(flux)
                .subscribe();
    }
}
