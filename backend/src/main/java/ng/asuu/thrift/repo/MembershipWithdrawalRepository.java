package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.MembershipWithdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipWithdrawalRepository extends JpaRepository<MembershipWithdrawal, Long> {
}
