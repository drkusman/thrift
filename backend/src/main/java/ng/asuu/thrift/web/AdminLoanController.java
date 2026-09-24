package ng.asuu.thrift.web;

import jakarta.validation.Valid;
import ng.asuu.thrift.domain.Loan;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.LoanBatchApplicationService;
import ng.asuu.thrift.service.LoanExportService;
import ng.asuu.thrift.service.LoanService;
import ng.asuu.thrift.service.LoanTypeService;
import ng.asuu.thrift.service.MemberService;
import ng.asuu.thrift.web.dto.AdminLoanApplicationRequest;
import ng.asuu.thrift.web.dto.DecisionRequest;
import ng.asuu.thrift.web.dto.LoanDto;
import ng.asuu.thrift.web.dto.ScheduleDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/loans")
public class AdminLoanController {
    private final LoanService loanService;
    private final LoanTypeService loanTypeService;
    private final MemberService memberService;
    private final LoanExportService loanExportService;
    private final LoanBatchApplicationService loanBatchApplicationService;

    public AdminLoanController(LoanService loanService, LoanTypeService loanTypeService,
                                MemberService memberService, LoanExportService loanExportService,
                                LoanBatchApplicationService loanBatchApplicationService) {
        this.loanService = loanService;
        this.loanTypeService = loanTypeService;
        this.memberService = memberService;
        this.loanExportService = loanExportService;
        this.loanBatchApplicationService = loanBatchApplicationService;
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

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        byte[] bytes = loanExportService.toExcel(loanService.pending(), loanTypeService.findAll(), membersById());
        return FileDownload.excel(bytes, "pending-loan-applications.xlsx");
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> exportPdf() throws IOException {
        byte[] bytes = loanExportService.toPdf(loanService.pending(), loanTypeService.findAll(), membersById());
        return FileDownload.pdf(bytes, "pending-loan-applications.pdf");
    }

    private Map<Long, Member> membersById() {
        return memberService.findAll().stream().collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    @PostMapping
    public LoanDto applyOnBehalf(@Valid @RequestBody AdminLoanApplicationRequest req) {
        Member member = memberService.require(req.memberId());
        Loan loan = loanService.applyOnBehalf(member, req.loanTypeId(), req.requestedAmount(), req.reason(),
                req.guarantorOneId(), req.guarantorTwoId());
        return LoanDto.of(loan);
    }

    @GetMapping("/apply-template.xlsx")
    public ResponseEntity<byte[]> applyTemplate() throws IOException {
        return FileDownload.excel(loanBatchApplicationService.template(), "loan-application-template.xlsx");
    }

    @PostMapping(value = "/apply-batch", consumes = "multipart/form-data")
    public String applyBatch(@RequestParam MultipartFile file) throws IOException {
        return loanBatchApplicationService.importBatch(file).text();
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
