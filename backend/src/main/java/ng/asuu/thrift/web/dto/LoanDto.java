package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.Loan;

public record LoanDto(Long id, String loanCode, Long memberId, Long loanTypeId, long requestedAmount, String reason,
                       Integer durationMonths, Long interestAmount, Long disbursedAmount, Long totalRepayable,
                       Long monthlyRepaymentAmount, String status, String appliedAt, String decisionNote) {
    public static LoanDto of(Loan l) {
        return new LoanDto(l.getId(), l.getLoanCode(), l.getMemberId(), l.getLoanTypeId(), l.getRequestedAmount(),
                l.getReason(), l.getDurationMonths(), l.getInterestAmount(), l.getDisbursedAmount(), l.getTotalRepayable(),
                l.getMonthlyRepaymentAmount(), l.getStatus().name(),
                l.getAppliedAt() == null ? null : l.getAppliedAt().toString(), l.getDecisionNote());
    }
}
