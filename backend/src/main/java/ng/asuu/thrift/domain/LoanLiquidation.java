package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Audit record of one liquidation event - links the three ledger entries it posted together (savings
 *  debit, admin fee debit, loan credit) so the full picture can be traced back from any one of them. See
 *  LoanLiquidationService for the business rule. */
@Entity @Table(name = "loan_liquidations") @Getter @Setter
public class LoanLiquidation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "loan_id", nullable = false) private Long loanId;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false) private long amount;
    @Column(name = "admin_fee", nullable = false) private long adminFee;
    @Column(name = "full_liquidation", nullable = false) private boolean fullLiquidation;
    @Column(name = "old_monthly_repayment_amount") private Long oldMonthlyRepaymentAmount;
    @Column(name = "new_monthly_repayment_amount") private Long newMonthlyRepaymentAmount;
    @Column(name = "savings_ledger_entry_id", nullable = false) private Long savingsLedgerEntryId;
    @Column(name = "fee_ledger_entry_id", nullable = false) private Long feeLedgerEntryId;
    @Column(name = "loan_ledger_entry_id", nullable = false) private Long loanLedgerEntryId;
    @Column(name = "performed_by", nullable = false) private Long performedBy;
    @Column(name = "performed_at", nullable = false) private LocalDateTime performedAt = LocalDateTime.now(ZoneOffset.UTC);
}
