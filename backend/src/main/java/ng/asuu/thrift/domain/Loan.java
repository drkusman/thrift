package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** All monetary amounts are computed once at approval time per the loan type's interest method:
 *  AT_SOURCE -> interestAmount = requestedAmount * rate/100; disbursedAmount = requestedAmount - interestAmount;
 *               totalRepayable = requestedAmount; monthlyRepaymentAmount = requestedAmount / durationMonths.
 *  BUILT_IN  -> interestAmount = requestedAmount * rate/100; disbursedAmount = requestedAmount;
 *               totalRepayable = requestedAmount + interestAmount; monthlyRepaymentAmount = totalRepayable / durationMonths.
 *  See LoanService.approve(). */
@Entity @Table(name = "loans") @Getter @Setter
public class Loan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "loan_code", unique = true) private String loanCode;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "loan_type_id", nullable = false) private Long loanTypeId;
    @Column(name = "requested_amount", nullable = false) private long requestedAmount;
    @Column private String reason;
    @Column(name = "duration_months") private Integer durationMonths;
    @Column(name = "interest_amount") private Long interestAmount;
    @Column(name = "disbursed_amount") private Long disbursedAmount;
    @Column(name = "total_repayable") private Long totalRepayable;
    @Column(name = "monthly_repayment_amount") private Long monthlyRepaymentAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private LoanStatus status = LoanStatus.PENDING;
    @Column(name = "applied_at", nullable = false) private LocalDateTime appliedAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "decided_by") private Long decidedBy;
    @Column(name = "decided_at") private LocalDateTime decidedAt;
    @Column(name = "decision_note") private String decisionNote;
    @Column(name = "disbursed_at") private LocalDateTime disbursedAt;

    public boolean isLegacy() { return loanCode != null && loanCode.startsWith("LEGACY-"); }
}
