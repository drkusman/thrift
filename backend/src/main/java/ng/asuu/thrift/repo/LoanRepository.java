package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.Loan;
import ng.asuu.thrift.domain.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByMemberIdOrderByAppliedAtDesc(Long memberId);
    List<Loan> findByStatusOrderByAppliedAtAsc(LoanStatus status);
    List<Loan> findByStatusInOrderByAppliedAtDesc(List<LoanStatus> statuses);
    List<Loan> findByMemberIdAndStatusOrderByDisbursedAtAsc(Long memberId, LoanStatus status);
}
