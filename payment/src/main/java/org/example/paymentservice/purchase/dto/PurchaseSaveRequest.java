package org.example.paymentservice.purchase.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseSaveRequest {
    private Long productId;
    private String merchantUid;
    private String impUid;
    private int productPrice;
    private int paidAmount;
    private String paymentMethod;
}
