package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.service.IncomeReportService.Income;
import ng.asuu.thrift.service.IncomeReportService.TypeAmount;

import java.util.List;

public record IncomeReportDto(String fiscalYearLabel, String since, String until, long interestOnLoans,
                               List<TypeAmountDto> interestByLoanType, long liquidationFees, long withdrawalCot,
                               long total) {
    public record TypeAmountDto(String type, long amount) {
        public static TypeAmountDto of(TypeAmount t) {
            return new TypeAmountDto(t.type(), t.amount());
        }
    }

    public static IncomeReportDto of(Income i) {
        return new IncomeReportDto(i.fiscalYearLabel(), i.since().toString(), i.until().toString(),
                i.interestOnLoans(), i.interestByLoanType().stream().map(TypeAmountDto::of).toList(),
                i.liquidationFees(), i.withdrawalCot(), i.total());
    }

    /** Current fiscal year alongside the previous one, for a quick side-by-side comparison. */
    public record ComparisonDto(IncomeReportDto previous, IncomeReportDto current) {}
}
