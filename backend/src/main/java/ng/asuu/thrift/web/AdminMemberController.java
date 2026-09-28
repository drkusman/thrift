package ng.asuu.thrift.web;

import jakarta.validation.Valid;
import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberRole;
import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.LedgerService;
import ng.asuu.thrift.service.MemberListExportService;
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
    private final MemberListExportService listExportService;

    public AdminMemberController(MemberService memberService, LedgerService ledgerService,
            TransactionExportService exportService, MemberListExportService listExportService) {
        this.memberService = memberService;
        this.ledgerService = ledgerService;
        this.exportService = exportService;
        this.listExportService = listExportService;
    }

    @GetMapping
    public List<MemberView> list() {
        return memberService.findAll().stream().map(MemberView::of).toList();
    }

    /** Mirrors the status/search filtering the Members admin page applies client-side, so the
     *  Excel/PDF export matches whatever's currently on screen. */
    private List<Member> filtered(MemberStatus status, String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return memberService.findAll().stream()
                .filter(m -> status == null || m.getStatus() == status)
                .filter(m -> q.isEmpty() || m.getRegno().toLowerCase().contains(q) || m.getFullName().toLowerCase().contains(q))
                .toList();
    }

    private static String filterLabel(MemberStatus status) {
        return status == null ? "All" : status.name();
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> exportListExcel(@RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) String query) throws IOException {
        List<Member> members = filtered(status, query);
        String filename = "members-" + (status == null ? "all" : status.name().toLowerCase()) + ".xlsx";
        return FileDownload.excel(listExportService.toExcel(members, filterLabel(status)), filename);
    }

    @GetMapping("/export.pdf")
    public ResponseEntity<byte[]> exportListPdf(@RequestParam(required = false) MemberStatus status,
            @RequestParam(required = false) String query) throws IOException {
        List<Member> members = filtered(status, query);
        String filename = "members-" + (status == null ? "all" : status.name().toLowerCase()) + ".pdf";
        return FileDownload.pdf(listExportService.toPdf(members, filterLabel(status)), filename);
    }

    @GetMapping("/{id}")
    public MemberView get(@PathVariable Long id) {
        return MemberView.of(memberService.require(id));
    }

    @PostMapping
    public MemberView register(@Valid @RequestBody RegisterMemberRequest req) {
        return MemberView.of(memberService.register(req.regno(), req.fullName(), req.phone(), req.email(), req.sex(),
                req.deptCode(), req.factCode(), req.payPoint(), req.monthlySavingsAmount(), req.bankId(), req.accountNo()));
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
