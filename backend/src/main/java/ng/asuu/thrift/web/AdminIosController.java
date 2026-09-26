package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.IosPayoutService;
import ng.asuu.thrift.web.dto.IosPayoutRequestDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    public long availableFor(@RequestParam Long memberId) {
        return iosPayoutService.availableToApply(memberId);
    }

    /** Admin applying on behalf of a member (e.g. the member asked in person, or by phone). Same rule
     *  as a member applying for themselves - the amount is always the member's own current unpaid IOS
     *  balance, never something the admin types in. */
    @PostMapping
    public IosPayoutRequestDto applyOnBehalf(@RequestBody ApplyOnBehalfRequest req) {
        return IosPayoutRequestDto.of(iosPayoutService.apply(req.memberId()));
    }

    @PostMapping("/{id}/mark-paid")
    public IosPayoutRequestDto markPaid(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id) {
        return IosPayoutRequestDto.of(iosPayoutService.markPaid(admin.getMember(), id));
    }

    @PostMapping("/{id}/reject")
    public IosPayoutRequestDto reject(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id) {
        return IosPayoutRequestDto.of(iosPayoutService.reject(admin.getMember(), id));
    }

    public record ApplyOnBehalfRequest(Long memberId) {}
}
