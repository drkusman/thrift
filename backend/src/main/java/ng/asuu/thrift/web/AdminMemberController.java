package ng.asuu.thrift.web;

import jakarta.validation.Valid;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberRole;
import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.LedgerService;
import ng.asuu.thrift.service.MemberService;
import ng.asuu.thrift.service.TransactionExportService;
import ng.asuu.thrift.web.dto.MemberView;
import ng.asuu.thrift.web.dto.RegisterMemberRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {
    private final MemberService memberService;
    private final LedgerService ledgerService;
    private final TransactionExportService exportService;

    public AdminMemberController(MemberService memberService, LedgerService ledgerService, TransactionExportService exportService) {
        this.memberService = memberService;
        this.ledgerService = ledgerService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<MemberView> list() {
        return memberService.findAll().stream().map(MemberView::of).toList();
    }

    @GetMapping("/{id}")
    public MemberView get(@PathVariable Long id) {
        return MemberView.of(memberService.require(id));
    }

    @PostMapping
    public MemberView register(@Valid @RequestBody RegisterMemberRequest req) {
        MemberRole role = req.role() == null || req.role().isBlank() ? MemberRole.MEMBER : MemberRole.valueOf(req.role());
        return MemberView.of(memberService.register(req.regno(), req.fullName(), req.phone(), req.email(), req.sex(),
                req.deptCode(), req.factCode(), req.payPoint(), role));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id) {
        memberService.adminResetPassword(memberService.require(id));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/status")
    public MemberView setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Member m = memberService.require(id);
        memberService.setStatus(m, MemberStatus.valueOf(body.get("status")));
        return MemberView.of(m);
    }

    @PostMapping("/{id}/role")
    public MemberView setRole(@AuthenticationPrincipal MemberPrincipal actor, @PathVariable Long id, @RequestBody Map<String, String> body) {
        Member m = memberService.require(id);
        memberService.setRole(m, MemberRole.valueOf(body.get("role")));
        return MemberView.of(m);
    }

    @GetMapping("/{id}/transactions")
    public List<ng.asuu.thrift.web.dto.LedgerEntryDto> transactions(@PathVariable Long id) {
        return ledgerService.history(id).stream().map(ng.asuu.thrift.web.dto.LedgerEntryDto::of).toList();
    }

    @GetMapping("/{id}/transactions/export.xlsx")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long id) throws IOException {
        Member m = memberService.require(id);
        return FileDownload.excel(exportService.toExcel(m, ledgerService.history(id)), "transactions-" + m.getRegno() + ".xlsx");
    }

    @GetMapping("/{id}/transactions/export.pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) throws IOException {
        Member m = memberService.require(id);
        return FileDownload.pdf(exportService.toPdf(m, ledgerService.history(id)), "transactions-" + m.getRegno() + ".pdf");
    }
}
