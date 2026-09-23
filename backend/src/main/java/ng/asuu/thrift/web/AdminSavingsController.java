package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.SavingsService;
import ng.asuu.thrift.web.dto.SavingsRequestDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/savings-requests")
public class AdminSavingsController {
    private final SavingsService savingsService;

    public AdminSavingsController(SavingsService savingsService) {
        this.savingsService = savingsService;
    }

    @GetMapping("/pending")
    public List<SavingsRequestDto> pending() {
        return savingsService.pending().stream().map(SavingsRequestDto::of).toList();
    }

    @PostMapping("/{id}/approve")
    public SavingsRequestDto approve(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id) {
        return SavingsRequestDto.of(savingsService.approve(admin.getMember(), id));
    }

    @PostMapping("/{id}/reject")
    public SavingsRequestDto reject(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id) {
        return SavingsRequestDto.of(savingsService.reject(admin.getMember(), id));
    }
}
