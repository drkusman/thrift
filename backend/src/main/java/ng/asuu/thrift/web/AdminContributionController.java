package ng.asuu.thrift.web;

import ng.asuu.thrift.domain.MonthlyContributionBatch;
import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.MonthlyContributionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/contributions")
public class AdminContributionController {
    private final MonthlyContributionService monthlyContributionService;

    public AdminContributionController(MonthlyContributionService monthlyContributionService) {
        this.monthlyContributionService = monthlyContributionService;
    }

    @GetMapping("/template.xlsx")
    public ResponseEntity<byte[]> template() throws IOException {
        return FileDownload.excel(monthlyContributionService.template(), "monthly-contribution-template.xlsx");
    }

    @GetMapping("/periods")
    public Set<String> uploadedPeriods() {
        return monthlyContributionService.uploadedPeriods();
    }

    @GetMapping("/batches")
    public List<BatchDto> batches() {
        return monthlyContributionService.batches().stream().map(BatchDto::of).toList();
    }

    @DeleteMapping("/batches/{id}")
    public void deleteBatch(@PathVariable Long id) {
        monthlyContributionService.deleteBatch(id);
    }

    @GetMapping("/batches/{id}/file")
    public ResponseEntity<byte[]> downloadBatchFile(@PathVariable Long id) {
        var batch = monthlyContributionService.requireBatch(id);
        if (batch.getFileBytes() == null) {
            // Uploaded before this feature existed - nothing was saved to serve back.
            return ResponseEntity.notFound().build();
        }
        String filename = batch.getFileName() != null ? batch.getFileName() : "contributions-" + batch.getPeriodMonth() + ".xlsx";
        return FileDownload.raw(batch.getFileBytes(), filename);
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Summary upload(@AuthenticationPrincipal MemberPrincipal admin,
                           @RequestParam String periodMonth,
                           @RequestParam MultipartFile file) throws IOException {
        var batch = monthlyContributionService.upload(admin.getMember(), periodMonth, file);
        return new Summary(batch.getId(), batch.getTotalRows(), batch.getMatchedRows(), batch.getTotalAmount());
    }

    public record Summary(Long batchId, int totalRows, int matchedRows, long totalAmount) {}

    public record BatchDto(Long id, String periodMonth, String fileName, String uploadedAt,
                            int totalRows, int matchedRows, long totalAmount, boolean hasFile) {
        static BatchDto of(MonthlyContributionBatch b) {
            return new BatchDto(b.getId(), b.getPeriodMonth(), b.getFileName(), b.getUploadedAt().toString(),
                    b.getTotalRows(), b.getMatchedRows(), b.getTotalAmount(), b.getFileBytes() != null);
        }
    }
}
