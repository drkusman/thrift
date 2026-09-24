package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "loan_types") @Getter @Setter
public class LoanType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 10) private String code;
    @Column(nullable = false) private String name;
    /** Percentage, e.g. 10 for 10%. */
    @Column(name = "interest_rate", nullable = false) private double interestRate;
    @Enumerated(EnumType.STRING) @Column(name = "interest_method", nullable = false) private InterestMethod interestMethod;
    @Column(name = "max_duration_months", nullable = false) private int maxDurationMonths;
    /** Amount band this loan type covers - either bound may be null for an open end (e.g. Main Loan
     *  has no max). Enforced in LoanService.apply(). */
    @Column(name = "min_amount") private Long minAmount;
    @Column(name = "max_amount") private Long maxAmount;
    /** Max number of this member's loans of this type that may be RUNNING/PULSED at once - null means
     *  no cap. Enforced in LoanService.apply(); e.g. Emergency Loan caps at 2, so a member must complete
     *  at least one of their two running Emergency Loans before a third is allowed. */
    @Column(name = "max_concurrent_active") private Integer maxConcurrentActive;
}
