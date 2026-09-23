package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.Bank;
import ng.asuu.thrift.repo.BankRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BankService {
    private final BankRepository bankRepository;

    public BankService(BankRepository bankRepository) {
        this.bankRepository = bankRepository;
    }

    public List<Bank> findAll() {
        return bankRepository.findAllByOrderByNameAsc();
    }

    public Bank require(Long id) {
        return bankRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank not found"));
    }

    @Transactional
    public Bank create(String bankCode, String name, String sortCode) {
        if (bankRepository.existsByBankCode(bankCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bank code " + bankCode + " already exists");
        }
        Bank b = new Bank();
        b.setBankCode(bankCode);
        b.setName(name);
        b.setSortCode(sortCode);
        return bankRepository.save(b);
    }

    @Transactional
    public void delete(Long id) {
        bankRepository.deleteById(id);
    }
}
