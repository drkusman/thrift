package ng.asuu.thrift.web;

import jakarta.validation.Valid;
import ng.asuu.thrift.domain.Loan;
import ng.asuu.thrift.domain.LoanStatus;
import ng.asuu.thrift.domain.LoanType;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.LedgerService;
import ng.asuu.thrift.service.LoanBatchApplicationService;
import ng.asuu.thrift.service.LoanExportService;
import ng.asuu.thrift.service.LoanListExportService;
import ng.asuu.thrift.service.LoanLiquidationService;
import ng.asuu.thrift.service.LoanService;
import ng.asuu.thrift.service.LoanTypeService;
import ng.asuu.thrift.service.MemberService;
import ng.asuu.thrift.service.TransactionExportService;
import ng.asuu.thrift.web.dto.AdminLoanApplicationRequest;
import ng.asuu.thrift.web.dto.DecisionRequest;
import ng.asuu.thrift.web.dto.LedgerEntryDto;
import ng.asuu.thrift.web.dto.LiquidateLoanRequest;
import ng.asuu.thrift.web.dto.LiquidationPreviewDto;
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
    private final LoanListExportService loanListExportService;
    private final LoanBatchApplicationService loanBatchApplicationService;
    private final LoanLiquidationService loanLiquidationService;
    private final LedgerService ledgerService;
    private final TransactionExportService transactionExportService;

    public AdminLoanController(LoanService loanService, LoanTypeService loanTypeService,
                                MemberService memberService, LoanExportService loanExportService,
                                LoanListExportService loanListExportService,
                                LoanBatchApplicationService loanBatchApplicationService,
                                LoanLiquidationService loanLiquidationService,
                                LedgerService ledgerService,
                                TransactionExportService transactionExportService) {
        this.loanService = loanService;
        this.loanTypeService = loanTypeService;
        this.memberService = memberService;
        this.loanExportService = loanExportService;
        this.loanListExportService = loanListExportService;
        this.loanBatchApplicationService = loanBatchApplicationService;
        this.loanLiquidationService = loanLiquidationService;
        this.ledgerService = ledgerService;
        this.transactionExportService = transactionExportService;
    }

    @GetMapping("/pending")
    public List<LoanDto> pending() {
        return loanService.pending().stream().map(LoanDto::of).toList();
    }

    @GetMapping("/member/{memberId}")
    public List<LoanDto> forMember(@PathVariable Long memberId) {
        return loanService.forMember(memberId).stream()
                .map(l -> LoanDto.of(l, loanService.balanceFor(l.getId())))
                .toList();
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

    @GetMapping("/by-status")
    public List<LoanDto> byStatus(@RequestParam LoanStatus status) {
        return loanService.byStatus(status).stream()
                .map(l -> LoanDto.of(l, loanService.balanceFor(l.getId())))
                .toList();
    }

    @GetMapping("/export-by-status.xlsx")
    public ResponseEntity<byte[]> exportByStatusExcel(@RequestParam LoanStatus status) throws IOException {
        byte[] bytes = loanListExportService.toExcel(loanService.byStatus(status), membersById(), loanTypesById(), statusLabel(status));
        return FileDownload.excel(bytes, "loans-" + status.name().toLowerCase() + ".xlsx");
    }

    @GetMapping("/export-by-status.pdf")
    public ResponseEntity<byte[]> exportByStatusPdf(@RequestParam LoanStatus status) throws IOException {
        byte[] bytes = loanListExportService.toPdf(loanService.byStatus(status), membersById(), loanTypesById(), statusLabel(status));
        return FileDownload.pdf(bytes, "loans-" + status.name().toLowerCase() + ".pdf");
    }

    private Map<Long, LoanType> loanTypesById() {
        return loanTypeService.findAll().stream().collect(Collectors.toMap(LoanType::getId, Function.identity()));
    }

    private static String statusLabel(LoanStatus status) {
        String name = status.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
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

    @PostMapping("/{id}/pulse")
    public LoanDto pulse(@PathVariable Long id) {
        return LoanDto.of(loanService.pulse(id));
    }

    @PostMapping("/{id}/resume")
    public LoanDto resume(@PathVariable Long id) {
        return LoanDto.of(loanService.resume(id));
    }

    @GetMapping("/{id}/liquidation-preview")
    public LiquidationPreviewDto liquidationPreview(@PathVariable Long id, @RequestParam long amount) {
        return LiquidationPreviewDto.of(loanLiquidationService.preview(id, amount));
    }

    @PostMapping("/{id}/liquidate")
    public LoanDto liquidate(@AuthenticationPrincipal MemberPrincipal admin, @PathVariable Long id, @RequestBody LiquidateLoanRequest req) {
        Loan loan = loanLiquidationService.liquidate(admin.getMember(), id, req.amount());
        return LoanDto.of(loan, loanService.balanceFor(loan.getId()));
    }

    @GetMapping("/{id}/transactions")
    public List<LedgerEntryDto> transactions(@PathVariable Long id) {
        return ledgerService.forLoan(id).stream().map(LedgerEntryDto::of).toList();
    }

    @GetMapping("/{id}/transactions/export.xlsx")
    public ResponseEntity<byte[]> transactionsExcel(@PathVariable Long id) throws IOException {
        Loan loan = loanService.require(id);
        Member member = memberService.require(loan.getMemberId());
        byte[] bytes = transactionExportService.toExcelForLoan(member, loan, ledgerService.forLoan(id));
        return FileDownload.excel(bytes, "loan-" + (loan.getLoanCode() != null ? loan.getLoanCode() : id) + "-transactions.xlsx");
    }

    @GetMapping("/{id}/transactions/export.pdf")
    public ResponseEntity<byte[]> transactionsPdf(@PathVariable Long id) throws IOException {
        Loan loan = loanService.require(id);
        Member member = memberService.require(loan.getMemberId());
        byte[] bytes = transactionExportService.toPdfForLoan(member, loan, ledgerService.forLoan(id));
        return FileDownload.pdf(bytes, "loan-" + (loan.getLoanCode() != null ? loan.getLoanCode() : id) + "-transactions.pdf");
    }
}
