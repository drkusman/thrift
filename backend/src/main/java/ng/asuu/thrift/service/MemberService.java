package ng.asuu.thrift.service;

import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberRole;
import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.domain.PasswordResetToken;
import ng.asuu.thrift.repo.MemberRepository;
import ng.asuu.thrift.repo.PasswordResetTokenRepository;
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
    private static final int RESET_TOKEN_TTL_MINUTES = 30;

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final MailService mailService;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
                          PasswordResetTokenRepository resetTokenRepository, MailService mailService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.resetTokenRepository = resetTokenRepository;
        this.mailService = mailService;
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
    public void touchLastSeen(Member principalMember) {
        Member member = require(principalMember.getId());
        member.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        memberRepository.save(member);
    }

    // ----- self-service password reset (forgot password) -----

    /** devModeLink: the raw reset link, only populated when there's no way to actually deliver it (no
     *  email on file, or SMTP not configured) - lets local/dev testing continue without a mail server.
     *  Nothing found -> both fields null; the caller shows the same generic message either way, so this
     *  can't be used to probe who's registered. */
    public record ResetRequestResult(String maskedEmail, String devModeLink) {}

    @Transactional
    public ResetRequestResult requestPasswordReset(String regno, String resetBaseUrl) {
        Member m = memberRepository.findByRegno(regno == null ? "" : regno.trim()).orElse(null);
        if (m == null || m.getStatus() != MemberStatus.ACTIVE) return new ResetRequestResult(null, null);

        PasswordResetToken t = new PasswordResetToken();
        t.setMemberId(m.getId());
        t.setToken(Codes.secureToken());
        t.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(RESET_TOKEN_TTL_MINUTES));
        String link = resetBaseUrl + "?token=" + t.getToken();

        boolean hasEmail = m.getEmail() != null && !m.getEmail().isBlank();
        if (hasEmail) {
            mailService.sendPasswordReset(m.getEmail(), m.getFullName(), link);
            if (mailService.isConfigured()) {
                resetTokenRepository.save(t);
                return new ResetRequestResult(maskEmail(m.getEmail()), null);
            }
        }
        resetTokenRepository.save(t);
        return new ResetRequestResult(null, link);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String local = email.substring(0, at), domain = email.substring(at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + domain;
    }

    public boolean isResetTokenValid(String token) {
        return token != null && resetTokenRepository.findByToken(token).map(PasswordResetToken::isValid).orElse(false);
    }

    @Transactional
    public void resetPasswordWithToken(String token, String password) {
        if (password == null || password.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }
        PasswordResetToken t = resetTokenRepository.findByToken(token == null ? "" : token).filter(PasswordResetToken::isValid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "This reset link is invalid or has expired. Please request a new one."));
        Member m = require(t.getMemberId());
        m.setPasswordHash(passwordEncoder.encode(password));
        m.setMustChangePassword(false);
        m.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        memberRepository.save(m);
        t.setUsedAt(LocalDateTime.now(ZoneOffset.UTC));
        resetTokenRepository.save(t);
    }
}
