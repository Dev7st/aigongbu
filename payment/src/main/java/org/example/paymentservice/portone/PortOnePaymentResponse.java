package org.example.paymentservice.portone;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class PortOnePaymentResponse {
    @JsonProperty("imp_uid")
    private String impUid;

    @JsonProperty("merchant_uid")
    private String merchantUid;

    private String status;               // 예: "paid"

    private Integer amount;              // 실제 결제 금액 (paid_amount)

    @JsonProperty("paid_at")
    private Long paidAt;                 // 결제 일시 (Unix timestamp)

    @JsonProperty("pay_method")
    private String paymentMethod;        // 예: "card", "kakao"

    @JsonProperty("custom_data")
    private Map<String, Object> customData;           // JSON string 형태로 들어옴
}
