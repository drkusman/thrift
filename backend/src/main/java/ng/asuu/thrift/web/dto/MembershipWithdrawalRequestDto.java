package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.MembershipWithdrawalRequest;

public record MembershipWithdrawalRequestDto(Long id, Long memberId, String status, String requestedAt,
                                              String decisionNote) {
    public static MembershipWithdrawalRequestDto of(MembershipWithdrawalRequest r) {
        return new MembershipWithdrawalRequestDto(r.getId(), r.getMemberId(), r.getStatus().name(),
                r.getRequestedAt().toString(), r.getDecisionNote());
    }
}
