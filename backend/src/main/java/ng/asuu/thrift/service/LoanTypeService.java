package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.InterestMethod;
import ng.asuu.thrift.domain.LoanType;
import ng.asuu.thrift.repo.LoanTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class LoanTypeService {
    private final LoanTypeRepository loanTypeRepository;

    public LoanTypeService(LoanTypeRepository loanTypeRepository) {
        this.loanTypeRepository = loanTypeRepository;
    }

    public List<LoanType> findAll() {
        return loanTypeRepository.findAllByOrderByNameAsc();
    }

    public LoanType require(Long id) {
        return loanTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan type not found"));
    }

    public LoanType requireByCode(String code) {
        return loanTypeRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan type not found: " + code));
    }

    @Transactional
    public LoanType create(String code, String name, double interestRate, InterestMethod method, int maxDurationMonths) {
        LoanType t = new LoanType();
        t.setCode(code);
        t.setName(name);
        t.setInterestRate(interestRate);
        t.setInterestMethod(method);
        t.setMaxDurationMonths(maxDurationMonths);
        return loanTypeRepository.save(t);
    }
}
