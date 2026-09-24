package ng.asuu.thrift.web.dto;

import ng.asuu.thrift.domain.Member;

/** Minimal, privacy-safe projection of a member for pickers other members use (e.g. loan referees) -
 *  no bank details, savings amount, phone/email, etc. */
public record MemberOption(Long id, String regno, String fullName) {
    public static MemberOption of(Member m) {
        return new MemberOption(m.getId(), m.getRegno(), m.getFullName());
    }
}
