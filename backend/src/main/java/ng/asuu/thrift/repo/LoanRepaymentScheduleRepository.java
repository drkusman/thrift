package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.LoanRepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanRepaymentScheduleRepository extends JpaRepository<LoanRepaymentSchedule, Long> {
    List<LoanRepaymentSchedule> findByLoanIdOrderByInstallmentNoAsc(Long loanId);
    Optional<LoanRepaymentSchedule> findFirstByLoanIdAndStatusNotOrderByInstallmentNoAsc(Long loanId, LoanRepaymentSchedule.ScheduleStatus status);
}
