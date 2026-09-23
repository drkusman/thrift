package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByRegno(String regno);
    boolean existsByRegno(String regno);
}
