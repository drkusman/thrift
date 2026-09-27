package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.AnnualIosExportService;
import ng.asuu.thrift.service.AnnualIosService;
import ng.asuu.thrift.web.dto.AnnualIosPreviewDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/ios-calculation")
public class AdminAnnualIosController {
    private final AnnualIosService annualIosService;
    private final AnnualIosExportService exportService;

    public AdminAnnualIosController(AnnualIosService annualIosService, AnnualIosExportService exportService) {
        this.annualIosService = annualIosService;
        this.exportService = exportService;
    }

    @GetMapping("/preview")
    public AnnualIosPreviewDto preview(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
                                        @RequestParam double ratePercent) {
        return AnnualIosPreviewDto.of(annualIosService.preview(since, ratePercent));
    }

    @PostMapping("/post")
    public PostResult post(@AuthenticationPrincipal MemberPrincipal admin, @RequestBody PostRequest req) {
        var batch = annualIosService.post(admin.getMember(), req.since(), req.ratePercent());
        return new PostResult(batch.getId(), batch.getMatchedRows(), batch.getTotalAmount());
    }

    /** Before posting: downloads the same preview the admin is looking at. Only valid pre-post, since the
     *  underlying balance query would double-count the IOS credits themselves once they exist - the
     *  frontend only shows this link while a batch hasn't been posted yet. */
    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> previewExcel(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
                                                @RequestParam double ratePercent) throws IOException {
        var preview = annualIosService.preview(since, ratePercent);
        byte[] bytes = exportService.iosListExcel(preview.rows(), "Interest on Savings - FY " + preview.fiscalYearLabel());
        return FileDownload.excel(bytes, "ios-fy-" + preview.fiscalYearLabel().replace("/", "-") + ".xlsx");
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> previewPdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since,
                                              @RequestParam double ratePercent) throws IOException {
        var preview = annualIosService.preview(since, ratePercent);
        byte[] bytes = exportService.iosListPdf(preview.rows(), "Interest on Savings - FY " + preview.fiscalYearLabel());
        return FileDownload.pdf(bytes, "ios-fy-" + preview.fiscalYearLabel().replace("/", "-") + ".pdf");
    }

    /** After posting: downloads the actual batch that was saved to the database (transType IOS1), so the
     *  figures always match what's in the ledger regardless of anything that's happened since. */
    @GetMapping("/batch/{batchId}/export.xlsx")
    public ResponseEntity<byte[]> batchExcel(@PathVariable Long batchId) throws IOException {
        var preview = annualIosService.forBatch(batchId);
        byte[] bytes = exportService.iosListExcel(preview.rows(), "Interest on Savings - FY " + preview.fiscalYearLabel() + " (posted)");
        return FileDownload.excel(bytes, "ios-fy-" + preview.fiscalYearLabel().replace("/", "-") + "-posted.xlsx");
    }

    @GetMapping("/batch/{batchId}/export.pdf")
    public ResponseEntity<byte[]> batchPdf(@PathVariable Long batchId) throws IOException {
        var preview = annualIosService.forBatch(batchId);
        byte[] bytes = exportService.iosListPdf(preview.rows(), "Interest on Savings - FY " + preview.fiscalYearLabel() + " (posted)");
        return FileDownload.pdf(bytes, "ios-fy-" + preview.fiscalYearLabel().replace("/", "-") + "-posted.pdf");
    }

    public record PostRequest(LocalDate since, double ratePercent) {}

    public record PostResult(Long batchId, int matchedRows, long totalAmount) {}
}
