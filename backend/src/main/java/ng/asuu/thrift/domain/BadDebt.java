package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** A member who withdrew from the cooperative with a negative overall equity - originalAmount is a
 *  snapshot of what they owed at withdrawal time (for record-keeping only). The actual amount still owed
 *  is read live from the ledger (see BadDebtService.owedNow()): the DR entry posted here at creation and
 *  every CR repayment since both live under TransCat.BAD_DEBT, the same pattern SAVINGS and LOAN use for
 *  their own running balances - so this debt shows up in the member's own transaction history and is
 *  auditable the same way everything else in the ledger is, rather than being a bare counter. */
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
