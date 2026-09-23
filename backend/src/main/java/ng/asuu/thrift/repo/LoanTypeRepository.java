package ng.asuu.thrift.repo;

import ng.asuu.thrift.domain.LoanType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoanTypeRepository extends JpaRepository<LoanType, Long> {
    Optional<LoanType> findByCode(String code);
    List<LoanType> findAllByOrderByNameAsc();
}
