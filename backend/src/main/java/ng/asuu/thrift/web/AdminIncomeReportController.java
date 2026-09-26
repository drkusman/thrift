package ng.asuu.thrift.web;

import ng.asuu.thrift.service.IncomeReportService;
import ng.asuu.thrift.web.dto.IncomeReportDto;
import ng.asuu.thrift.web.dto.IncomeReportDto.ComparisonDto;
import ng.asuu.thrift.web.dto.IncomeTransactionRowDto;
import ng.asuu.thrift.web.dto.LoanInterestRowDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/income-report")
public class AdminIncomeReportController {
    private final IncomeReportService incomeReportService;

    public AdminIncomeReportController(IncomeReportService incomeReportService) {
        this.incomeReportService = incomeReportService;
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

    @GetMapping("/liquidation-fees")
    public List<IncomeTransactionRowDto> liquidationFees(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        return incomeReportService.ledgerTransactions(since, "FEES").stream().map(IncomeTransactionRowDto::of).toList();
    }

    @GetMapping("/withdrawal-cot")
    public List<IncomeTransactionRowDto> withdrawalCot(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        return incomeReportService.ledgerTransactions(since, "COT").stream().map(IncomeTransactionRowDto::of).toList();
    }
}
