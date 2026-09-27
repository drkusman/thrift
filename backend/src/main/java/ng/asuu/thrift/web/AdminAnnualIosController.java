package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.AnnualIosService;
import ng.asuu.thrift.web.dto.AnnualIosPreviewDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/ios-calculation")
public class AdminAnnualIosController {
    private final AnnualIosService annualIosService;

    public AdminAnnualIosController(AnnualIosService annualIosService) {
        this.annualIosService = annualIosService;
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

    public record PostRequest(LocalDate since, double ratePercent) {}

    public record PostResult(Long batchId, int matchedRows, long totalAmount) {}
}
