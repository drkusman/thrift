package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.MonthlyContributionBatchRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyContributionBatchRowRepository extends JpaRepository<MonthlyContributionBatchRow, Long> {
    List<MonthlyContributionBatchRow> findByBatchId(Long batchId);
    List<MonthlyContributionBatchRow> findByBatchIdAndMatchedFalse(Long batchId);
}
