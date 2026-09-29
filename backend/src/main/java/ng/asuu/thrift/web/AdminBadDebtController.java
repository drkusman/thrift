package ng.asuu.thrift.web;

import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.service.BadDebtService;
import ng.asuu.thrift.web.dto.RepaymentRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/bad-debts")
public class AdminBadDebtController {
    private final BadDebtService badDebtService;

    public AdminBadDebtController(BadDebtService badDebtService) {
        this.badDebtService = badDebtService;
    }

    @GetMapping
    public List<BadDebtService.Row> outstanding() {
        return badDebtService.outstanding();
    }

    @PostMapping("/{id}/repayment")
    public List<BadDebtService.Row> recordRepayment(@AuthenticationPrincipal MemberPrincipal admin,
            @PathVariable Long id, @RequestBody RepaymentRequest req) {
        badDebtService.recordRepayment(admin.getMember(), id, req.amount());
        return badDebtService.outstanding();
    }
}
