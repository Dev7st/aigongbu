package org.example.paymentservice.purchase.entity;

import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Builder
@Table("rollback_failure")
public record RollBackFailure(
        @Id Long id,

        @Column("purchase_id")
        Long purchaseId,

        @Column("imp_uid")
        String impUid,

        int amount,

        String reason,

        @Column("created_at")
        Instant createdAt
) {}
