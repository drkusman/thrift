package ng.asuu.thrift.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** regno (the legacy staff/member ID, e.g. "A01249") is the login identifier - the legacy phone column
 *  is unreliable (many rows share the literal placeholder "070"), so it's kept as informational only. */
@Entity @Table(name = "members") @Getter @Setter
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 20) private String regno;
    @Column(name = "full_name", nullable = false) private String fullName;
    private String phone;
    private String email;
    private String sex;
    @Column(name = "dept_code") private String deptCode;
    @Column(name = "fact_code") private String factCode;
    @Column(name = "pay_point") private String payPoint;
    @Column(name = "bank_id") private Long bankId;
    @Column(name = "account_no") private String accountNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MemberStatus status = MemberStatus.ACTIVE;
    @Column(name = "monthly_savings_amount", nullable = false) private long monthlySavingsAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MemberRole role = MemberRole.MEMBER;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(name = "must_change_password", nullable = false) private boolean mustChangePassword = true;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    @Column(name = "last_seen_at") private LocalDateTime lastSeenAt;

    public boolean isActive() { return status == MemberStatus.ACTIVE; }
    public boolean isAdmin() { return role == MemberRole.ADMIN; }
    public boolean isStaff() { return role == MemberRole.ADMIN || role == MemberRole.FIN_SEC; }
}
