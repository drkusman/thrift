package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity @Table(name = "loan_repayment_schedules") @Getter @Setter
public class LoanRepaymentSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "loan_id", nullable = false) private Long loanId;
    @Column(name = "installment_no", nullable = false) private int installmentNo;
    @Column(name = "due_date", nullable = false) private LocalDate dueDate;
    @Column(name = "amount_due", nullable = false) private long amountDue;
    @Column(name = "amount_paid", nullable = false) private long amountPaid = 0;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ScheduleStatus status = ScheduleStatus.PENDING;
    @Column(name = "paid_at") private java.time.LocalDateTime paidAt;

    public enum ScheduleStatus { PENDING, PAID, PARTIAL, OVERDUE }
}
