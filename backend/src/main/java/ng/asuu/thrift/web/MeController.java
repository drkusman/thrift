package ng.asuu.thrift.web;

import jakarta.validation.Valid;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.*;
import ng.asuu.thrift.web.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/me")
public class MeController {
    private final MemberService memberService;
    private final LedgerService ledgerService;
    private final LoanService loanService;
    private final SavingsService savingsService;
    private final IosPayoutService iosPayoutService;
    private final TransactionExportService exportService;

    public MeController(MemberService memberService, LedgerService ledgerService, LoanService loanService,
                         SavingsService savingsService, IosPayoutService iosPayoutService, TransactionExportService exportService) {
        this.memberService = memberService;
        this.ledgerService = ledgerService;
        this.loanService = loanService;
        this.savingsService = savingsService;
        this.iosPayoutService = iosPayoutService;
        this.exportService = exportService;
    }

    @GetMapping
    public MemberView me(@AuthenticationPrincipal MemberPrincipal principal) {
        return MemberView.of(principal.getMember());
    }

    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody ChangePasswordRequest req) {
        memberService.changePassword(principal.getMember(), req.currentPassword(), req.newPassword());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bank-account")
    public MemberView setBankAccount(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody BankAccountRequest req) {
        Member updated = memberService.updateBankAccount(principal.getMember(), req.bankId(), req.accountNo());
        return MemberView.of(updated);
    }

    @GetMapping("/transactions")
    public List<LedgerEntryDto> transactions(@AuthenticationPrincipal MemberPrincipal principal) {
        return ledgerService.history(principal.getMember().getId()).stream().map(LedgerEntryDto::of).toList();
    }

    @GetMapping("/balance")
    public BalanceView balance(@AuthenticationPrincipal MemberPrincipal principal) {
        Long id = principal.getMember().getId();
        // Re-fetch rather than trust the session-cached principal, since monthlySavingsAmount can
        // change mid-session (an approved SavingsAmountChangeRequest updates it independently).
        Member member = memberService.require(id);
        long totalSavings = ledgerService.savingsBalance(id);
        long loanBalance = loanService.outstandingBalance(id);
        long monthlySavings = member.getMonthlySavingsAmount();
        long loanPayments = loanService.activeMonthlyRepayment(id);
        return new BalanceView(monthlySavings, loanPayments, monthlySavings + loanPayments,
                totalSavings, loanBalance, totalSavings - loanBalance);
    }

    @GetMapping("/transactions/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@AuthenticationPrincipal MemberPrincipal principal) throws IOException {
        Member member = principal.getMember();
        byte[] bytes = exportService.toExcel(member, ledgerService.history(member.getId()));
        return FileDownload.excel(bytes, "transactions-" + member.getRegno() + ".xlsx");
    }

    @GetMapping("/transactions/export.pdf")
    public ResponseEntity<byte[]> exportPdf(@AuthenticationPrincipal MemberPrincipal principal) throws IOException {
        Member member = principal.getMember();
        byte[] bytes = exportService.toPdf(member, ledgerService.history(member.getId()));
        return FileDownload.pdf(bytes, "transactions-" + member.getRegno() + ".pdf");
    }

    @GetMapping("/loans")
    public List<LoanDto> loans(@AuthenticationPrincipal MemberPrincipal principal) {
        return loanService.forMember(principal.getMember().getId()).stream()
                .map(loan -> LoanDto.of(loan, loan.getTotalRepayable() == null ? null : loanService.balanceFor(loan.getId())))
                .toList();
    }

    @PostMapping("/loans")
    public LoanDto applyForLoan(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody LoanApplicationRequest req) {
        return LoanDto.of(loanService.apply(principal.getMember(), req.loanTypeId(), req.requestedAmount(), req.reason(),
                req.guarantorOneId(), req.guarantorTwoId()));
    }

    @PostMapping("/loans/{loanId}/change-guarantor")
    public LoanDto changeGuarantor(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long loanId,
                                    @Valid @RequestBody ChangeGuarantorRequest req) {
        return LoanDto.of(loanService.changeGuarantor(principal.getMember(), loanId, req.slot(), req.newGuarantorId()));
    }

    @GetMapping("/loans/{loanId}/schedule")
    public List<ScheduleDto> loanSchedule(@AuthenticationPrincipal MemberPrincipal principal, @PathVariable Long loanId) {
        var loan = loanService.require(loanId);
        if (!loan.getMemberId().equals(principal.getMember().getId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        }
        return loanService.scheduleFor(loanId).stream().map(ScheduleDto::of).toList();
    }

    @GetMapping("/savings-requests")
    public List<SavingsRequestDto> savingsRequests(@AuthenticationPrincipal MemberPrincipal principal) {
        return savingsService.forMember(principal.getMember().getId()).stream().map(SavingsRequestDto::of).toList();
    }

    @PostMapping("/savings-requests")
    public SavingsRequestDto requestSavingsChange(@AuthenticationPrincipal MemberPrincipal principal, @Valid @RequestBody SavingsChangeAmountRequest req) {
        return SavingsRequestDto.of(savingsService.requestChange(principal.getMember(), req.amount()));
    }

    @GetMapping("/ios-available")
    public long iosAvailable(@AuthenticationPrincipal MemberPrincipal principal) {
        return iosPayoutService.availableToApply(principal.getMember().getId());
    }

    @GetMapping("/ios-requests")
    public List<IosPayoutRequestDto> iosRequests(@AuthenticationPrincipal MemberPrincipal principal) {
        return iosPayoutService.forMember(principal.getMember().getId()).stream().map(IosPayoutRequestDto::of).toList();
    }

    @PostMapping("/ios-requests")
    public IosPayoutRequestDto applyForIos(@AuthenticationPrincipal MemberPrincipal principal) {
        return IosPayoutRequestDto.of(iosPayoutService.apply(principal.getMember().getId()));
    }

    public record BalanceView(long monthlySavings, long loanPayments, long monthlyDeductions,
                               long totalSavings, long loanBalance, long equity) {}
}
