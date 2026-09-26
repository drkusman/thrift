package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.IosPayoutRequest;
import ng.asuu.thrift.domain.IosPayoutRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IosPayoutRequestRepository extends JpaRepository<IosPayoutRequest, Long> {
    List<IosPayoutRequest> findByMemberIdOrderByRequestedAtDesc(Long memberId);
    List<IosPayoutRequest> findByStatusOrderByRequestedAtAsc(RequestStatus status);
    List<IosPayoutRequest> findByMemberIdAndStatus(Long memberId, RequestStatus status);
}
