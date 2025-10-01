package org.example.paymentservice.purchase.entity;

import lombok.Builder;
import org.example.paymentservice.purchase.type.Status;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Builder
@Table("purchase")
public record Purchase(
        @Id Long id,

        @Column("user_id")
        Long userId,

        @Column("product_id")
        Long productId,

        @Column("merchant_uid")
        String merchantUid,

        @Column("imp_uid")
        String impUid,

        @Column("product_price")
        int productPrice,

        @Column("paid_amount")
        int paidAmount,

        @Column("payment_method")
        String paymentMethod,

        Status status,

        @Column("created_at")
        @CreatedDate
        Instant createdAt,

        @Column("is_verified")
        boolean isVerified,

        String reason
) {
        public Purchase verify(String impUid) {
                return new Purchase(
                        this.id,
                        this.userId,
                        this.productId,
                        this.merchantUid,
                        impUid,
                        this.productPrice,
                        this.paidAmount,
                        this.paymentMethod,
                        Status.COMPLETED,
                        this.createdAt,
                        true,
                        null
                );
        }

        public Purchase cancel() {
                if (!this.status.equals(Status.COMPLETED)) {
                        throw new IllegalStateException("검증까지 완료된 결제만 취소 처리할 수 있습니다.");
                }

                return new Purchase(
                        this.id,
                        this.userId,
                        this.productId,
                        this.merchantUid,
                        this.impUid,
                        this.productPrice,
                        this.paidAmount,
                        this.paymentMethod,
                        Status.CANCELED,
                        this.createdAt,
                        this.isVerified,
                        null
                );
        }

        public Purchase rollback() {
                return new Purchase(
                        this.id,
                        this.userId,
                        this.productId,
                        this.merchantUid,
                        this.impUid,
                        this.productPrice,
                        this.paidAmount,
                        this.paymentMethod,
                        Status.ROLLBACK_REQUESTED,
                        this.createdAt,
                        false,
                        null
                );
        };

        public Purchase refund() {
                if (!this.status.equals(Status.ROLLBACK_REQUESTED)) {
                        throw new IllegalStateException("롤백 요청 받은 결제만 환불 처리할 수 있습니다.");
                }

                return new Purchase(
                        this.id,
                        this.userId,
                        this.productId,
                        this.merchantUid,
                        this.impUid,
                        this.productPrice,
                        this.paidAmount,
                        this.paymentMethod,
                        Status.REFUNDED,
                        this.createdAt,
                        false,
                        null
                );
        };

        public Purchase fail(String reason) {
                return new Purchase(
                        this.id,
                        this.userId,
                        this.productId,
                        this.merchantUid,
                        this.impUid,
                        this.productPrice,
                        this.paidAmount,
                        this.paymentMethod,
                        Status.FAILED,
                        this.createdAt,
                        false,
                        reason
                );
        }
}
