package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberRole;
import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.repo.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Member> findAll() {
        return memberRepository.findAll();
    }

    public Member require(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    public Member requireByRegno(String regno) {
        return memberRepository.findByRegno(regno)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
    }

    @Transactional
    public Member register(String regno, String fullName, String phone, String email, String sex,
                            String deptCode, String factCode, String payPoint, MemberRole role) {
        if (memberRepository.existsByRegno(regno)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A member with regno " + regno + " already exists");
        }
        Member m = new Member();
        m.setRegno(regno);
        m.setFullName(fullName);
        m.setPhone(phone);
        m.setEmail(email);
        m.setSex(sex);
        m.setDeptCode(deptCode);
        m.setFactCode(factCode);
        m.setPayPoint(payPoint);
        m.setRole(role == null ? MemberRole.MEMBER : role);
        m.setStatus(MemberStatus.ACTIVE);
        m.setMonthlySavingsAmount(20000);
        m.setPasswordHash(passwordEncoder.encode(regno));
        m.setMustChangePassword(true);
        return memberRepository.save(m);
    }

    @Transactional
    public void changePassword(Member principalMember, String currentPassword, String newPassword) {
        // principalMember comes from the session-cached security principal, not this transaction's
        // persistence context - re-fetch a managed instance so the update actually flushes to the DB.
        Member member = require(principalMember.getId());
        if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be at least 6 characters");
        }
        member.setPasswordHash(passwordEncoder.encode(newPassword));
        member.setMustChangePassword(false);
        member.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        memberRepository.save(member);
    }

    @Transactional
    public void adminResetPassword(Member target) {
        target.setPasswordHash(passwordEncoder.encode(target.getRegno()));
        target.setMustChangePassword(true);
        target.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        memberRepository.save(target);
    }

    @Transactional
    public Member updateBankAccount(Member principalMember, Long bankId, String accountNo) {
        Member member = require(principalMember.getId());
        member.setBankId(bankId);
        member.setAccountNo(accountNo);
        member.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return memberRepository.save(member);
    }

    @Transactional
    public void setStatus(Member member, MemberStatus status) {
        member.setStatus(status);
        member.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        memberRepository.save(member);
    }

    @Transactional
    public void setRole(Member member, MemberRole role) {
        member.setRole(role);
        member.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        memberRepository.save(member);
    }

    @Transactional
    public void touchLastSeen(Member member) {
        member.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        memberRepository.save(member);
    }
}
