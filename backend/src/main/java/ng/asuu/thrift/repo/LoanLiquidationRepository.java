package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.LoanLiquidation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanLiquidationRepository extends JpaRepository<LoanLiquidation, Long> {
}
