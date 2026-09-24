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
    /** Two other active members guaranteeing the applicant. Required for new applications (enforced in
     *  LoanService.apply(), not at the column level) but left nullable so historical/legacy loans that
     *  predate this requirement don't break. Each must ACCEPT before the loan can be approved - see
     *  LoanService.approve()/respondToGuarantee(). An applicant may swap out a guarantor who is still
     *  PENDING or has REJECTED (not one who already ACCEPTED) via LoanService.changeGuarantor(). */
    @Column(name = "guarantor_one_id") private Long guarantorOneId;
    @Column(name = "guarantor_two_id") private Long guarantorTwoId;
    @Enumerated(EnumType.STRING) @Column(name = "guarantor_one_status", nullable = false) private GuaranteeStatus guarantorOneStatus = GuaranteeStatus.PENDING;
    @Enumerated(EnumType.STRING) @Column(name = "guarantor_two_status", nullable = false) private GuaranteeStatus guarantorTwoStatus = GuaranteeStatus.PENDING;

    public boolean isLegacy() { return loanCode != null && loanCode.startsWith("LEGACY-"); }
    public boolean bothGuarantorsAccepted() {
        return guarantorOneStatus == GuaranteeStatus.ACCEPTED && guarantorTwoStatus == GuaranteeStatus.ACCEPTED;
    }
}
