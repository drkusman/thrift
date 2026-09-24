package ng.asuu.thrift.web;

import ng.asuu.thrift.service.LegacyImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/import")
public class AdminImportController {
    private final LegacyImportService importService;

    public AdminImportController(LegacyImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/banks", consumes = "multipart/form-data")
    public String banks(@RequestParam MultipartFile file) throws IOException {
        return importService.importBanks(text(file)).text();
    }

    @PostMapping(value = "/loan-types", consumes = "multipart/form-data")
    public String loanTypes(@RequestParam MultipartFile file) throws IOException {
        return importService.importLoanTypes(text(file)).text();
    }

    @PostMapping(value = "/members", consumes = "multipart/form-data")
    public String members(@RequestParam MultipartFile file) throws IOException {
        return importService.importMembers(text(file)).text();
    }

    @PostMapping(value = "/ledger", consumes = "multipart/form-data")
    public String ledger(@RequestParam MultipartFile file) throws IOException {
        return importService.importLedger(text(file)).text();
    }

    @PostMapping(value = "/historical-loans", consumes = "multipart/form-data")
    public String historicalLoans(@RequestParam MultipartFile file) throws IOException {
        return importService.importHistoricalLoans(text(file)).text();
    }

    @PostMapping("/backfill-legacy-repayments")
    public String backfillLegacyRepayments() {
        return importService.backfillLegacyMonthlyRepayments().text();
    }

    private static String text(MultipartFile file) throws IOException {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }
}
