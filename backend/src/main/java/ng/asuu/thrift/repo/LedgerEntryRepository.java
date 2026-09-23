package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByMemberIdOrderByDateDesc(Long memberId);
    List<LedgerEntry> findByLoanIdOrderByDateAsc(Long loanId);
    boolean existsByTransCode(String transCode);
}
