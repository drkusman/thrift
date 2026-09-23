package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.Member;

public record MemberView(
        Long id, String regno, String fullName, String phone, String email, String sex,
        String deptCode, String factCode, String payPoint, Long bankId, String accountNo,
        String status, long monthlySavingsAmount, String role, boolean mustChangePassword
) {
    public static MemberView of(Member m) {
        return new MemberView(m.getId(), m.getRegno(), m.getFullName(), m.getPhone(), m.getEmail(), m.getSex(),
                m.getDeptCode(), m.getFactCode(), m.getPayPoint(), m.getBankId(), m.getAccountNo(),
                m.getStatus().name(), m.getMonthlySavingsAmount(), m.getRole().name(), m.isMustChangePassword());
    }
}
