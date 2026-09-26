package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.service.LedgerService.UnpaidIosCredit;

public record UnpaidIosCreditDto(Long ledgerEntryId, String date, String description, long amount) {
    public static UnpaidIosCreditDto of(UnpaidIosCredit c) {
        return new UnpaidIosCreditDto(c.ledgerEntryId(), c.date().toString(), c.description(), c.amount());
    }
}
