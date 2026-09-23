package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.Bank;

public record BankDto(Long id, String bankCode, String name, String sortCode) {
    public static BankDto of(Bank b) {
        return new BankDto(b.getId(), b.getBankCode(), b.getName(), b.getSortCode());
    }
}
