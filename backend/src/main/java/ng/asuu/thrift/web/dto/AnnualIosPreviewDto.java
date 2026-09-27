package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.service.AnnualIosService.Preview;
import ng.asuu.thrift.service.AnnualIosService.Row;

import java.util.List;

public record AnnualIosPreviewDto(String fiscalYearLabel, String since, String until, double ratePercent,
                                   List<RowDto> rows, long totalAmount, boolean alreadyRun, long totalCooperativeIncome) {
    public record RowDto(Long memberId, String regno, String fullName, long savingsBalance, long iosAmount) {
        public static RowDto of(Row r) {
            return new RowDto(r.memberId(), r.regno(), r.fullName(), r.savingsBalance(), r.iosAmount());
        }
    }

    public static AnnualIosPreviewDto of(Preview p) {
        return new AnnualIosPreviewDto(p.fiscalYearLabel(), p.since().toString(), p.until().toString(),
                p.ratePercent(), p.rows().stream().map(RowDto::of).toList(), p.totalAmount(), p.alreadyRun(),
                p.totalCooperativeIncome());
    }
}
