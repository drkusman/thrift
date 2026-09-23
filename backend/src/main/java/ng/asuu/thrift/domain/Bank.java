package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Admin-managed list of banks members can select when setting up their account details. Legacy internal
 *  GL codes (e.g. "001 Savings", "002 Loan") are excluded on import - see the import service. */
@Entity @Table(name = "banks") @Getter @Setter
public class Bank {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "bank_code", nullable = false, unique = true, length = 10) private String bankCode;
    @Column(nullable = false) private String name;
    @Column(name = "sort_code") private String sortCode;
}
