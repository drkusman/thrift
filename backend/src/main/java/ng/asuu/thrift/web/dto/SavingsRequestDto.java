package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.SavingsAmountChangeRequest;

public record SavingsRequestDto(Long id, Long memberId, long requestedAmount, String status, String requestedAt) {
    public static SavingsRequestDto of(SavingsAmountChangeRequest r) {
        return new SavingsRequestDto(r.getId(), r.getMemberId(), r.getRequestedAmount(), r.getStatus().name(), r.getRequestedAt().toString());
    }
}
