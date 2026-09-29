package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A member who withdrew from the cooperative with a negative savings balance - originalAmount is a
 *  snapshot of what they owed at withdrawal time (for record-keeping only); the actual amount still owed
 *  is always read live from their ledger (see BadDebtService), since repayments post as ordinary ledger
 *  entries rather than being tracked as a separate running total here. */
@Entity @Table(name = "bad_debts") @Getter @Setter
public class BadDebt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "membership_withdrawal_id") private Long membershipWithdrawalId;
    @Column(name = "original_amount", nullable = false) private long originalAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private BadDebtStatus status = BadDebtStatus.OUTSTANDING;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "cleared_at") private LocalDateTime clearedAt;
}
