package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Audit record of one membership withdrawal - links the two ledger entries it posted (the COT deduction
 *  and the payout) so the full picture traces back from either one. See MembershipWithdrawalService. */
@Entity @Table(name = "membership_withdrawals") @Getter @Setter
public class MembershipWithdrawal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "total_savings", nullable = false) private long totalSavings;
    @Column(name = "total_loan", nullable = false) private long totalLoan;
    @Column(nullable = false) private long balance;
    @Column(nullable = false) private long cot;
    @Column(name = "withdrawable_amount", nullable = false) private long withdrawableAmount;
    @Column(name = "cot_ledger_entry_id", nullable = false) private Long cotLedgerEntryId;
    @Column(name = "payout_ledger_entry_id", nullable = false) private Long payoutLedgerEntryId;
    @Column(name = "performed_by", nullable = false) private Long performedBy;
    @Column(name = "performed_at", nullable = false) private LocalDateTime performedAt = LocalDateTime.now(ZoneOffset.UTC);
}
