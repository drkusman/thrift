package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.LedgerEntry;

public record LedgerEntryDto(Long id, String transCode, Long memberId, long amount, String date, String description,
                              String transType, String transCat, String drCrStatus, Long loanId, String source) {
    public static LedgerEntryDto of(LedgerEntry e) {
        return new LedgerEntryDto(e.getId(), e.getTransCode(), e.getMemberId(), e.getAmount(), e.getDate().toString(),
                e.getDescription(), e.getTransType(), e.getTransCat().name(), e.getDrCrStatus().name(), e.getLoanId(), e.getSource().name());
    }
}
