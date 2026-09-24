package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.LoanService;
import ng.asuu.thrift.service.MemberService;
import ng.asuu.thrift.web.dto.GuaranteeRequestDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Guarantee requests directed AT the current member (they've been named as a guarantor on someone
 *  else's loan application) - separate from /api/me/loans, which is the member's OWN applications. */
@RestController
@RequestMapping("/api/me/guarantee-requests")
public class GuaranteeController {
    private final LoanService loanService;
    private final MemberService memberService;

    public GuaranteeController(LoanService loanService, MemberService memberService) {
        this.loanService = loanService;
        this.memberService = memberService;
    }

    @GetMapping
    public List<GuaranteeRequestDto> myGuaranteeRequests(@AuthenticationPrincipal MemberPrincipal principal) {
        Long myId = principal.getMember().getId();
        return loanService.guaranteeRequestsFor(myId).stream()
                .map(loan -> GuaranteeRequestDto.of(loan, memberService.require(loan.getMemberId()), myId))
                .toList();
    }

    @PostMapping("/{loanId}/accept")
    public void accept(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long loanId) {
        loanService.respondToGuarantee(principal.getMember(), loanId, true);
    }

    @PostMapping("/{loanId}/reject")
    public void reject(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long loanId) {
        loanService.respondToGuarantee(principal.getMember(), loanId, false);
    }
}
