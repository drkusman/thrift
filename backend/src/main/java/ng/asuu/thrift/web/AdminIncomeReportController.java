package ng.asuu.thrift.web;

import ng.asuu.thrift.service.IncomeReportExportService;
import ng.asuu.thrift.service.IncomeReportService;
import ng.asuu.thrift.web.dto.IncomeReportDto;
import ng.asuu.thrift.web.dto.IncomeReportDto.ComparisonDto;
import ng.asuu.thrift.web.dto.IncomeTransactionRowDto;
import ng.asuu.thrift.web.dto.LoanInterestRowDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/income-report")
public class AdminIncomeReportController {
    private final IncomeReportService incomeReportService;
    private final IncomeReportExportService exportService;

    public AdminIncomeReportController(IncomeReportService incomeReportService, IncomeReportExportService exportService) {
        this.incomeReportService = incomeReportService;
        this.exportService = exportService;
    }

    /** Current fiscal year alongside the previous one, for a quick side-by-side comparison. */
    @GetMapping
    public ComparisonDto report() {
        return new ComparisonDto(
                IncomeReportDto.of(incomeReportService.previousFiscalYear()),
                IncomeReportDto.of(incomeReportService.currentFiscalYear()));
    }

    /** The loans behind an interest total - `since` is that fiscal year's own start date (as returned in
     *  the report above), loanType narrows to one type (as named in interestByLoanType()), omit it to see
     *  every interest-bearing loan disbursed that year. */
    @GetMapping("/interest-loans")
    public List<LoanInterestRowDto> interestLoans(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
                                                   @RequestParam(required = false) String loanType) {
        return incomeReportService.interestLoans(since, loanType).stream().map(LoanInterestRowDto::of).toList();
    }

    @GetMapping("/interest-loans/export.xlsx")
    public ResponseEntity<byte[]> interestLoansExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
                                                      @RequestParam(required = false) String loanType) throws IOException {
        var rows = incomeReportService.interestLoans(since, loanType);
        String title = "Interest on Loans" + (loanType != null && !loanType.isBlank() ? " - " + loanType : "") + " - FY starting " + since;
        return FileDownload.excel(exportService.loanInterestExcel(rows, title), "interest-loans-" + since + ".xlsx");
    }

    @GetMapping("/interest-loans/export.pdf")
    public ResponseEntity<byte[]> interestLoansPdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
                                                    @RequestParam(required = false) String loanType) throws IOException {
        var rows = incomeReportService.interestLoans(since, loanType);
        String title = "Interest on Loans" + (loanType != null && !loanType.isBlank() ? " - " + loanType : "") + " - FY starting " + since;
        return FileDownload.pdf(exportService.loanInterestPdf(rows, title), "interest-loans-" + since + ".pdf");
    }

    @GetMapping("/liquidation-fees")
    public List<IncomeTransactionRowDto> liquidationFees(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        return incomeReportService.ledgerTransactions(since, "FEES").stream().map(IncomeTransactionRowDto::of).toList();
    }

    @GetMapping("/liquidation-fees/export.xlsx")
    public ResponseEntity<byte[]> liquidationFeesExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) throws IOException {
        var rows = incomeReportService.ledgerTransactions(since, "FEES");
        return FileDownload.excel(exportService.transactionsExcel(rows, "Admin Charge on Liquidations - FY starting " + since),
                "liquidation-fees-" + since + ".xlsx");
    }

    @GetMapping("/liquidation-fees/export.pdf")
    public ResponseEntity<byte[]> liquidationFeesPdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) throws IOException {
        var rows = incomeReportService.ledgerTransactions(since, "FEES");
        return FileDownload.pdf(exportService.transactionsPdf(rows, "Admin Charge on Liquidations - FY starting " + since),
                "liquidation-fees-" + since + ".pdf");
    }

    @GetMapping("/withdrawal-cot")
    public List<IncomeTransactionRowDto> withdrawalCot(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        return incomeReportService.ledgerTransactions(since, "COT").stream().map(IncomeTransactionRowDto::of).toList();
    }

    @GetMapping("/withdrawal-cot/export.xlsx")
    public ResponseEntity<byte[]> withdrawalCotExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) throws IOException {
        var rows = incomeReportService.ledgerTransactions(since, "COT");
        return FileDownload.excel(exportService.transactionsExcel(rows, "Commission on Turnover (COT) - FY starting " + since),
                "withdrawal-cot-" + since + ".xlsx");
    }

    @GetMapping("/withdrawal-cot/export.pdf")
    public ResponseEntity<byte[]> withdrawalCotPdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) throws IOException {
        var rows = incomeReportService.ledgerTransactions(since, "COT");
        return FileDownload.pdf(exportService.transactionsPdf(rows, "Commission on Turnover (COT) - FY starting " + since),
                "withdrawal-cot-" + since + ".pdf");
    }

    @GetMapping("/application-form-sales")
    public List<IncomeTransactionRowDto> applicationFormSales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        return incomeReportService.ledgerTransactions(since, "LAF").stream().map(IncomeTransactionRowDto::of).toList();
    }

    @GetMapping("/application-form-sales/export.xlsx")
    public ResponseEntity<byte[]> applicationFormSalesExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) throws IOException {
        var rows = incomeReportService.ledgerTransactions(since, "LAF");
        return FileDownload.excel(exportService.transactionsExcel(rows, "Sale of Application Forms - FY starting " + since),
                "application-form-sales-" + since + ".xlsx");
    }

    @GetMapping("/application-form-sales/export.pdf")
    public ResponseEntity<byte[]> applicationFormSalesPdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) throws IOException {
        var rows = incomeReportService.ledgerTransactions(since, "LAF");
        return FileDownload.pdf(exportService.transactionsPdf(rows, "Sale of Application Forms - FY starting " + since),
                "application-form-sales-" + since + ".pdf");
    }
}
