package ng.asuu.thrift.web;

import ng.asuu.thrift.domain.InterestMethod;
import ng.asuu.thrift.service.BankService;
import ng.asuu.thrift.service.LoanTypeService;
import ng.asuu.thrift.web.dto.BankDto;
import ng.asuu.thrift.web.dto.LoanTypeDto;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminReferenceController {
    private final BankService bankService;
    private final LoanTypeService loanTypeService;

    public AdminReferenceController(BankService bankService, LoanTypeService loanTypeService) {
        this.bankService = bankService;
        this.loanTypeService = loanTypeService;
    }

    @PostMapping("/banks")
    public BankDto createBank(@RequestBody Map<String, String> body) {
        return BankDto.of(bankService.create(body.get("bankCode"), body.get("name"), body.get("sortCode")));
    }

    @DeleteMapping("/banks/{id}")
    public void deleteBank(@PathVariable Long id) {
        bankService.delete(id);
    }

    @PostMapping("/loan-types")
    public LoanTypeDto createLoanType(@RequestBody Map<String, Object> body) {
        return LoanTypeDto.of(loanTypeService.create(
                (String) body.get("code"), (String) body.get("name"),
                ((Number) body.get("interestRate")).doubleValue(),
                InterestMethod.valueOf((String) body.get("interestMethod")),
                ((Number) body.get("maxDurationMonths")).intValue()));
    }
}
