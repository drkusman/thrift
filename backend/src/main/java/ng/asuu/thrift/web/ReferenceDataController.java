package ng.asuu.thrift.web;

import ng.asuu.thrift.service.BankService;
import ng.asuu.thrift.service.LoanTypeService;
import ng.asuu.thrift.web.dto.BankDto;
import ng.asuu.thrift.web.dto.LoanTypeDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReferenceDataController {
    private final BankService bankService;
    private final LoanTypeService loanTypeService;

    public ReferenceDataController(BankService bankService, LoanTypeService loanTypeService) {
        this.bankService = bankService;
        this.loanTypeService = loanTypeService;
    }

    @GetMapping("/api/banks")
    public List<BankDto> banks() {
        return bankService.findAll().stream().map(BankDto::of).toList();
    }

    @GetMapping("/api/loan-types")
    public List<LoanTypeDto> loanTypes() {
        return loanTypeService.findAll().stream().map(LoanTypeDto::of).toList();
    }
}
