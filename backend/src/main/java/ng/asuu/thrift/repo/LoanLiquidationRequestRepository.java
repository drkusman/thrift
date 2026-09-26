package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.LoanLiquidationRequest;
import ng.asuu.thrift.domain.LoanLiquidationRequest.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanLiquidationRequestRepository extends JpaRepository<LoanLiquidationRequest, Long> {
    List<LoanLiquidationRequest> findByMemberIdOrderByRequestedAtDesc(Long memberId);
    List<LoanLiquidationRequest> findByStatusOrderByRequestedAtAsc(RequestStatus status);
    List<LoanLiquidationRequest> findByLoanIdAndStatus(Long loanId, RequestStatus status);
}
