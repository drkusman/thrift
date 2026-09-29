package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.BadDebt;
import ng.asuu.thrift.domain.BadDebtStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BadDebtRepository extends JpaRepository<BadDebt, Long> {
    List<BadDebt> findByStatusOrderByCreatedAtAsc(BadDebtStatus status);
    Optional<BadDebt> findByMemberIdAndStatus(Long memberId, BadDebtStatus status);
}
