package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.Loan;

public record LoanDto(Long id, String loanCode, Long memberId, Long loanTypeId, long requestedAmount, String reason,
                       Integer durationMonths, Long interestAmount, Long disbursedAmount, Long totalRepayable,
                       Long monthlyRepaymentAmount, Long balance, String status, String appliedAt, String decisionNote,
                       Long guarantorOneId, Long guarantorTwoId, String guarantorOneStatus, String guarantorTwoStatus,
                       Long applicantEquity) {
    public static LoanDto of(Loan l) {
        return of(l, null);
    }

    /** balance is this loan's own outstanding amount (its disbursement minus its repayments so far) -
     *  null where it hasn't been computed, e.g. a loan that was just applied for or isn't disbursed yet. */
    public static LoanDto of(Loan l, Long balance) {
        return of(l, balance, null);
    }

    /** applicantEquity is the member's own current savings-minus-loan-balance (see
     *  MemberBalanceReportService), shown alongside a pending application so an admin can judge whether
     *  it's in good standing before approving - only populated where the caller bothers to look it up
     *  (currently just the pending-applications list), null everywhere else. */
    public static LoanDto of(Loan l, Long balance, Long applicantEquity) {
        return new LoanDto(l.getId(), l.getLoanCode(), l.getMemberId(), l.getLoanTypeId(), l.getRequestedAmount(),
                l.getReason(), l.getDurationMonths(), l.getInterestAmount(), l.getDisbursedAmount(), l.getTotalRepayable(),
                l.getMonthlyRepaymentAmount(), balance, l.getStatus().name(),
                l.getAppliedAt() == null ? null : l.getAppliedAt().toString(), l.getDecisionNote(),
                l.getGuarantorOneId(), l.getGuarantorTwoId(),
                l.getGuarantorOneStatus().name(), l.getGuarantorTwoStatus().name(), applicantEquity);
    }
}
