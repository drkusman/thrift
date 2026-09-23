package ng.asuu.thrift.web;

import jakarta.validation.Valid;
import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.LoanService;
import ng.asuu.thrift.web.dto.DecisionRequest;
import ng.asuu.thrift.web.dto.LoanDto;
import ng.asuu.thrift.web.dto.ScheduleDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/loans")
public class AdminLoanController {
    private final LoanService loanService;

    public AdminLoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/pending")
    public List<LoanDto> pending() {
        return loanService.pending().stream().map(LoanDto::of).toList();
    }

    @GetMapping("/member/{memberId}")
    public List<LoanDto> forMember(@PathVariable Long memberId) {
        return loanService.forMember(memberId).stream().map(LoanDto::of).toList();
    }

    @GetMapping("/{id}/schedule")
    public List<ScheduleDto> schedule(@PathVariable Long id) {
        return loanService.scheduleFor(id).stream().map(ScheduleDto::of).toList();
    }

    @PostMapping("/{id}/approve")
    public LoanDto approve(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        return LoanDto.of(loanService.approve(admin.getMember(), id, req == null ? null : req.note()));
    }

    @PostMapping("/{id}/reject")
    public LoanDto reject(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        return LoanDto.of(loanService.reject(admin.getMember(), id, req == null ? null : req.note()));
    }
}
