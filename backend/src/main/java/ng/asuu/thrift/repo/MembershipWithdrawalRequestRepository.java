package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.MembershipWithdrawalRequest;
import ng.asuu.thrift.domain.MembershipWithdrawalRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembershipWithdrawalRequestRepository extends JpaRepository<MembershipWithdrawalRequest, Long> {
    List<MembershipWithdrawalRequest> findByMemberIdOrderByRequestedAtDesc(Long memberId);
    List<MembershipWithdrawalRequest> findByStatusOrderByRequestedAtAsc(RequestStatus status);
    List<MembershipWithdrawalRequest> findByMemberIdAndStatus(Long memberId, RequestStatus status);
}
