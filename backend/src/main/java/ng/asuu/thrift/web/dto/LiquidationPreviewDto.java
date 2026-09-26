package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.service.LoanLiquidationService.Preview;

public record LiquidationPreviewDto(long currentBalance, long amount, long adminFee, boolean fullLiquidation,
                                     long newBalance, long savingsBalance, boolean sufficientSavings,
                                     Integer remainingInstallments, Long proposedMonthlyRepayment) {
    public static LiquidationPreviewDto of(Preview p) {
        return new LiquidationPreviewDto(p.currentBalance(), p.amount(), p.adminFee(), p.fullLiquidation(),
                p.newBalance(), p.savingsBalance(), p.sufficientSavings(), p.remainingInstallments(),
                p.proposedMonthlyRepayment());
    }
}
