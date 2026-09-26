package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.IosPayoutService;
import ng.asuu.thrift.web.dto.IosPayoutRequestDto;
import ng.asuu.thrift.web.dto.UnpaidIosCreditDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/ios-requests")
public class AdminIosController {
    private final IosPayoutService iosPayoutService;

    public AdminIosController(IosPayoutService iosPayoutService) {
        this.iosPayoutService = iosPayoutService;
    }

    @GetMapping("/pending")
    public List<IosPayoutRequestDto> pending() {
        return iosPayoutService.pending().stream().map(IosPayoutRequestDto::of).toList();
    }

    @GetMapping("/available")
    public List<UnpaidIosCreditDto> availableFor(@RequestParam Long memberId) {
        return iosPayoutService.unpaidCredits(memberId).stream().map(UnpaidIosCreditDto::of).toList();
    }

    /** The pending queue as a Monthly Upload-ready workbook (KIND=IOS2) - re-uploading it with "IOS?" set
     *  to Yes auto-resolves each matching request, which is what actually clears it from this queue. */
    @GetMapping("/pending/export.xlsx")
    public ResponseEntity<byte[]> exportPending() throws IOException {
        return FileDownload.excel(iosPayoutService.exportPendingXlsx(), "pending-ios-payouts.xlsx");
    }

    /** Admin applying on behalf of a member (e.g. the member asked in person, or by phone). Same rule
     *  as a member applying for themselves - the amount is always that one credit's own amount, never
     *  something the admin types in, and one credit (one year's interest) at a time. */
    @PostMapping
    public IosPayoutRequestDto applyOnBehalf(@RequestBody ApplyOnBehalfRequest req) {
        return IosPayoutRequestDto.of(iosPayoutService.apply(req.memberId(), req.ledgerEntryId()));
    }

    @PostMapping("/{id}/mark-paid")
    public IosPayoutRequestDto markPaid(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id) {
        return IosPayoutRequestDto.of(iosPayoutService.markPaid(admin.getMember(), id));
    }

    @PostMapping("/{id}/reject")
    public IosPayoutRequestDto reject(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id) {
        return IosPayoutRequestDto.of(iosPayoutService.reject(admin.getMember(), id));
    }

    public record ApplyOnBehalfRequest(Long memberId, Long ledgerEntryId) {}
}
