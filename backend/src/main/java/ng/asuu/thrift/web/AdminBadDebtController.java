package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.BadDebtExportService;
import ng.asuu.thrift.service.BadDebtService;
import ng.asuu.thrift.web.dto.RepaymentRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/bad-debts")
public class AdminBadDebtController {
    private final BadDebtService badDebtService;
    private final BadDebtExportService badDebtExportService;

    public AdminBadDebtController(BadDebtService badDebtService, BadDebtExportService badDebtExportService) {
        this.badDebtService = badDebtService;
        this.badDebtExportService = badDebtExportService;
    }

    @GetMapping
    public List<BadDebtService.Row> outstanding() {
        return badDebtService.outstanding();
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        return FileDownload.excel(badDebtExportService.toExcel(badDebtService.outstanding()), "bad-debt-list.xlsx");
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> exportPdf() throws IOException {
        return FileDownload.pdf(badDebtExportService.toPdf(badDebtService.outstanding()), "bad-debt-list.pdf");
    }

    @PostMapping("/{id}/repayment")
    public List<BadDebtService.Row> recordRepayment(@AuthenticationPrincipal MemberPrincipal admin,
            @PathVariable Long id, @RequestBody RepaymentRequest req) {
        badDebtService.recordRepayment(admin.getMember(), id, req.amount());
        return badDebtService.outstanding();
    }
}
