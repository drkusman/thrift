package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.LoanLiquidationRequest;

public record LoanLiquidationRequestDto(Long id, Long loanId, Long memberId, long requestedAmount, String status,
                                         String requestedAt, String decisionNote) {
    public static LoanLiquidationRequestDto of(LoanLiquidationRequest r) {
        return new LoanLiquidationRequestDto(r.getId(), r.getLoanId(), r.getMemberId(), r.getRequestedAmount(),
                r.getStatus().name(), r.getRequestedAt().toString(), r.getDecisionNote());
    }
}
