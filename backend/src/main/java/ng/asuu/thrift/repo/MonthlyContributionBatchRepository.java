package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.MonthlyContributionBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthlyContributionBatchRepository extends JpaRepository<MonthlyContributionBatch, Long> {
    List<MonthlyContributionBatch> findAllByOrderByUploadedAtDesc();
}
