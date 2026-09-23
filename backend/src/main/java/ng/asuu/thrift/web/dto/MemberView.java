package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.Member;

import java.time.LocalDateTime;

public record MemberView(
        Long id, String regno, String fullName, String phone, String email, String sex,
        String deptCode, String factCode, String payPoint, Long bankId, String accountNo,
        String status, long monthlySavingsAmount, String role, boolean mustChangePassword,
        String lastSeenAt
) {
    public static MemberView of(Member m) {
        return of(m, m.getLastSeenAt());
    }

    /** Lets the login response report the PREVIOUS last-seen time (captured before it's touched to now),
     *  so the member sees "last seen" as in their prior session, not this one. */
    public static MemberView of(Member m, LocalDateTime lastSeenAt) {
        return new MemberView(m.getId(), m.getRegno(), m.getFullName(), m.getPhone(), m.getEmail(), m.getSex(),
                m.getDeptCode(), m.getFactCode(), m.getPayPoint(), m.getBankId(), m.getAccountNo(),
                m.getStatus().name(), m.getMonthlySavingsAmount(), m.getRole().name(), m.isMustChangePassword(),
                lastSeenAt == null ? null : lastSeenAt.toString());
    }
}
