package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.service.IncomeReportService.TransactionRow;

public record IncomeTransactionRowDto(String regno, String fullName, String date, String description, long amount) {
    public static IncomeTransactionRowDto of(TransactionRow r) {
        return new IncomeTransactionRowDto(r.regno(), r.fullName(), r.date(), r.description(), r.amount());
    }
}
