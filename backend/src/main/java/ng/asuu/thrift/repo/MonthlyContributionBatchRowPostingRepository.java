package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.MonthlyContributionBatchRowPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyContributionBatchRowPostingRepository extends JpaRepository<MonthlyContributionBatchRowPosting, Long> {
    List<MonthlyContributionBatchRowPosting> findByBatchRowIdIn(List<Long> batchRowIds);
}
