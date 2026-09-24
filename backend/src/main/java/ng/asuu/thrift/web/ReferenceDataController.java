package ng.asuu.thrift.web;

import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.service.BankService;
import ng.asuu.thrift.service.LoanTypeService;
import ng.asuu.thrift.service.MemberService;
import ng.asuu.thrift.web.dto.BankDto;
import ng.asuu.thrift.web.dto.LoanTypeDto;
import ng.asuu.thrift.web.dto.MemberOption;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReferenceDataController {
    private final BankService bankService;
    private final LoanTypeService loanTypeService;
    private final MemberService memberService;

    public ReferenceDataController(BankService bankService, LoanTypeService loanTypeService, MemberService memberService) {
        this.bankService = bankService;
        this.loanTypeService = loanTypeService;
        this.memberService = memberService;
    }

    @GetMapping("/api/banks")
    public List<BankDto> banks() {
        return bankService.findAll().stream().map(BankDto::of).toList();
    }

    @GetMapping("/api/loan-types")
    public List<LoanTypeDto> loanTypes() {
        return loanTypeService.findAll().stream().map(LoanTypeDto::of).toList();
    }

    /** Active members only, minimal fields - used for pickers other members use, e.g. selecting the
     *  two loan referees. Any authenticated member can call this (not admin-only). */
    @GetMapping("/api/members/active")
    public List<MemberOption> activeMembers() {
        return memberService.findAll().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .map(MemberOption::of)
                .toList();
    }
}
