package org.example.paymentservice.purchase.entity;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Builder
@Table("cancel_failure")
public record CancelFailure(
        @Id Long id,

        @Column("purchase_id")
        Long purchaseId,

        @Column("imp_uid")
        String impUid,

        Integer amount,

        String reason,

        @Column("created_at")
        Instant createdAt
) {
    public CancelFailure cancel() {
        return new CancelFailure(
                this.id,
                this.purchaseId,
                this.impUid,
                this.amount,
                this.reason,
                this.createdAt
        );
    }
}
