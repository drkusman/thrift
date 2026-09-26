package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.MemberService;
import ng.asuu.thrift.service.MembershipWithdrawalService;
import ng.asuu.thrift.web.dto.DecisionRequest;
import ng.asuu.thrift.web.dto.MemberView;
import ng.asuu.thrift.web.dto.MembershipWithdrawalRequestDto;
import ng.asuu.thrift.web.dto.MembershipWithdrawalSummaryDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMembershipWithdrawalController {
    private final MembershipWithdrawalService membershipWithdrawalService;
    private final MemberService memberService;

    public AdminMembershipWithdrawalController(MembershipWithdrawalService membershipWithdrawalService, MemberService memberService) {
        this.membershipWithdrawalService = membershipWithdrawalService;
        this.memberService = memberService;
    }

    @GetMapping("/{id}/withdrawal-summary")
    public MembershipWithdrawalSummaryDto summary(@PathVariable Long id) {
        return MembershipWithdrawalSummaryDto.of(membershipWithdrawalService.summary(id));
    }

    @PostMapping("/{id}/withdraw")
    public MemberView withdraw(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id) {
        membershipWithdrawalService.withdraw(admin.getMember(), id);
        return MemberView.of(memberService.require(id));
    }

    @GetMapping("/withdrawal-requests/pending")
    public List<MembershipWithdrawalRequestDto> pendingRequests() {
        return membershipWithdrawalService.pendingRequests().stream().map(MembershipWithdrawalRequestDto::of).toList();
    }

    @PostMapping("/withdrawal-requests/{id}/approve")
    public MembershipWithdrawalRequestDto approve(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id) {
        return MembershipWithdrawalRequestDto.of(membershipWithdrawalService.approve(admin.getMember(), id));
    }

    @PostMapping("/withdrawal-requests/{id}/reject")
    public MembershipWithdrawalRequestDto reject(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id, @RequestBody(required = false) DecisionRequest req) {
        return MembershipWithdrawalRequestDto.of(membershipWithdrawalService.reject(admin.getMember(), id, req == null ? null : req.note()));
    }
}
