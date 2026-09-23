package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.SavingsAmountChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SavingsAmountChangeRequestRepository extends JpaRepository<SavingsAmountChangeRequest, Long> {
    List<SavingsAmountChangeRequest> findByMemberIdOrderByRequestedAtDesc(Long memberId);
    List<SavingsAmountChangeRequest> findByStatusOrderByRequestedAtAsc(SavingsAmountChangeRequest.RequestStatus status);
}
