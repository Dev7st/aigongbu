package org.example.paymentservice.purchase.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.paymentservice.purchase.dto.PurchaseCancelRequest;
import org.example.paymentservice.purchase.dto.PurchaseSaveRequest;
import org.example.paymentservice.purchase.dto.PurchaseSummaryResponse;
import org.example.paymentservice.purchase.entity.Purchase;
import org.example.paymentservice.purchase.service.PurchaseService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pay")
@Slf4j
public class PurchaseApiController {

    private final PurchaseService purchaseService;

    @PostMapping("/save")
    public Mono<Purchase> save(
            @RequestBody PurchaseSaveRequest purchaseSaveRequest,
            @RequestHeader ("X-User-id") Long userId
    ) {
        return purchaseService.savePurchase(purchaseSaveRequest, userId);
    }

    @PostMapping("/cancel")
    public Mono<Purchase> cancel(@RequestBody PurchaseCancelRequest purchaseCancelRequest,
                                 @RequestHeader ("X-User-id") Long userId) {
        return purchaseService.cancelPayment(purchaseCancelRequest, userId);
    }

    @GetMapping("/read")
    public Flux<PurchaseSummaryResponse> getMyPurchases(@RequestHeader("X-User-id") Long userId) {
        return purchaseService.findByUserId(userId);
    }
}