package org.example.paymentservice.lecture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class LectureClient {

    private final WebClient lectureWebClient;

    public Mono<LectureInfo> getLectureInfo(Long lectureId) {
        return lectureWebClient.get()
                .uri("/lectures/{id}", lectureId)
                .retrieve()
                .bodyToMono(LectureInfo.class)
                .doOnError(e -> log.error("강의 정보 조회 실패: lectureId: {}", lectureId, e))
                .onErrorResume(e -> Mono.just(new LectureInfo("알 수 없음", "미상", 0)));
    }

    public Mono<LectureDiscountResponse> reserveDiscount(Long productId) {
        return lectureWebClient.post()
                .uri("/lecture-discounts/{productId}/reserve", productId)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), res -> Mono.just(new IllegalStateException("할인 정보 없음 또는 할인 기간 불일치")))
                .onStatus(status -> status.is5xxServerError(), res -> Mono.error(new IllegalStateException("강의 서비스 오류")))
                .bodyToMono(LectureDiscountResponse.class);
    }
}
