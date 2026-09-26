package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.service.MemberBalanceReportService.Row;

public record MemberBalanceRowDto(Long memberId, String regno, String fullName, long savingsBalance,
                                   long loanBalance, long netEquity) {
    public static MemberBalanceRowDto of(Row r) {
        return new MemberBalanceRowDto(r.memberId(), r.regno(), r.fullName(), r.savingsBalance(), r.loanBalance(), r.netEquity());
    }
}
