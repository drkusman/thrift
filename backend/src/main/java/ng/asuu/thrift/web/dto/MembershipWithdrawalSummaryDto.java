package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.service.MembershipWithdrawalService.Summary;

public record MembershipWithdrawalSummaryDto(long totalSavings, long totalLoan, long balance, long cot,
                                              long withdrawableAmount, boolean canWithdraw) {
    public static MembershipWithdrawalSummaryDto of(Summary s) {
        return new MembershipWithdrawalSummaryDto(s.totalSavings(), s.totalLoan(), s.balance(), s.cot(),
                s.withdrawableAmount(), s.canWithdraw());
    }
}
