package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.MonthlyContributionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/contributions")
public class AdminContributionController {
    private final MonthlyContributionService monthlyContributionService;

    public AdminContributionController(MonthlyContributionService monthlyContributionService) {
        this.monthlyContributionService = monthlyContributionService;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Summary upload(@AuthenticationPrincipal MemberPrincipal admin,
                           @RequestParam String periodMonth,
                           @RequestParam MultipartFile file) throws IOException {
        var batch = monthlyContributionService.upload(admin.getMember(), periodMonth, file);
        return new Summary(batch.getId(), batch.getTotalRows(), batch.getMatchedRows(), batch.getTotalAmount());
    }

    public record Summary(Long batchId, int totalRows, int matchedRows, long totalAmount) {}
}
