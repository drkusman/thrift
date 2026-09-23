package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.Bank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankRepository extends JpaRepository<Bank, Long> {
    Optional<Bank> findByBankCode(String bankCode);
    boolean existsByBankCode(String bankCode);
    List<Bank> findAllByOrderByNameAsc();
}
