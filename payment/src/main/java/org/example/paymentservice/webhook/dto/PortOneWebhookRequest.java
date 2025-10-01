package org.example.paymentservice.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class PortOneWebhookRequest {
    @JsonProperty("imp_uid")
    private String impUid;

    @JsonProperty("merchant_uid")
    private String merchantUid;

    private String status;                 // 예: "paid", "cancelled"

    private Integer amount;               // 결제된 금액

    @JsonProperty("paid_at")
    private Long paidAt;                  // UNIX timestamp

    @JsonProperty("custom_data")
    private Map<String, Object> customData;  // userId, productId 등이 들어있을 수 있음
}
