package ng.asuu.thrift.web;

import ng.asuu.thrift.service.MemberBalanceExportService;
import ng.asuu.thrift.service.MemberBalanceReportService;
import ng.asuu.thrift.web.dto.MemberBalanceRowDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/member-balances")
public class AdminMemberBalanceController {
    private final MemberBalanceReportService reportService;
    private final MemberBalanceExportService exportService;

    public AdminMemberBalanceController(MemberBalanceReportService reportService, MemberBalanceExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<MemberBalanceRowDto> report(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return reportService.report(asOfDate).stream().map(MemberBalanceRowDto::of).toList();
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) throws IOException {
        byte[] bytes = exportService.toExcel(reportService.report(asOfDate), asOfDate);
        return FileDownload.excel(bytes, "member-balances-" + asOfDate + ".xlsx");
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) throws IOException {
        byte[] bytes = exportService.toPdf(reportService.report(asOfDate), asOfDate);
        return FileDownload.pdf(bytes, "member-balances-" + asOfDate + ".pdf");
    }
}
