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
}
