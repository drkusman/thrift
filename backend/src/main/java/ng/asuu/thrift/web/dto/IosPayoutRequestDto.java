package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.IosPayoutRequest;

public record IosPayoutRequestDto(Long id, Long memberId, long requestedAmount, Long sourceLedgerEntryId,
                                   String status, String requestedAt) {
    public static IosPayoutRequestDto of(IosPayoutRequest r) {
        return new IosPayoutRequestDto(r.getId(), r.getMemberId(), r.getRequestedAmount(), r.getSourceLedgerEntryId(),
                r.getStatus().name(), r.getRequestedAt().toString());
    }
}
