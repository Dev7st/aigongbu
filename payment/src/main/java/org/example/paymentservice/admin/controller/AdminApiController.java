package org.example.paymentservice.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.paymentservice.admin.dto.CancelFailureResponse;
import org.example.paymentservice.admin.dto.PurchaseFailureResponse;
import org.example.paymentservice.admin.dto.RollBackFailureResponse;
import org.example.paymentservice.admin.service.AdminService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pay/admin")
public class AdminApiController {

    private final AdminService adminService;

    @GetMapping("/refund/fail")
    public Flux<RollBackFailureResponse> getFailures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return adminService.getFailedRefund(pageable);
    }

    @GetMapping("/verify/fail")
    public Flux<PurchaseFailureResponse> getFailedPurchases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return adminService.getFailedPurchases(pageable);
    }

    @GetMapping("/cancel/fail")
    public Flux<CancelFailureResponse> getCancelFailures(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return adminService.getFailedCancels(pageable);
    }

    @PostMapping("/force/refund/{id}")
    public Mono<ResponseEntity<String>> force(@PathVariable Long id) {
        return adminService.forceRefund(id)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/retry/verify/{id}")
    public Mono<ResponseEntity<String>> retry(@PathVariable Long id) {
        return adminService.retryVerification(id)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/force/cancel/{purchaseId}")
    public Mono<ResponseEntity<String>> cancel(@PathVariable Long purchaseId) {
        return adminService.forceCancel(purchaseId)
                .map(ResponseEntity::ok);
    }
}
