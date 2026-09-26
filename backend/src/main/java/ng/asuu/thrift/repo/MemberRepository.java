package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByRegno(String regno);
    boolean existsByRegno(String regno);
    List<Member> findByStatusOrderByFullNameAsc(MemberStatus status);
}
