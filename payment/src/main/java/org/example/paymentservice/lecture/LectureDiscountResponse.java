package org.example.paymentservice.lecture;

import java.math.BigDecimal;

public record LectureDiscountResponse(
        boolean applied,
        BigDecimal discountRate
) {}
