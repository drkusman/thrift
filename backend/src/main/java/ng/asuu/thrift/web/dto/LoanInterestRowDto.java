package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.service.IncomeReportService.LoanInterestRow;

public record LoanInterestRowDto(String regno, String fullName, String loanCode, String loanType,
                                  String disbursedAt, long interestAmount) {
    public static LoanInterestRowDto of(LoanInterestRow r) {
        return new LoanInterestRowDto(r.regno(), r.fullName(), r.loanCode(), r.loanType(), r.disbursedAt(), r.interestAmount());
    }
}
