package ng.asuu.thrift.config;

import ng.asuu.thrift.domain.Member;
import ng.asuu.thrift.domain.MemberRole;
import ng.asuu.thrift.domain.MemberStatus;
import ng.asuu.thrift.repo.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Ensures a single admin login exists on first startup, so there's always a way in before any
 *  member data has been registered or imported. Configure via THRIFT_ADMIN_REGNO/PASSWORD. */
@Component
public class AdminSeeder implements CommandLineRunner {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${thrift.admin.regno}")
    private String adminRegno;
    @Value("${thrift.admin.password}")
    private String adminPassword;

    public AdminSeeder(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (memberRepository.existsByRegno(adminRegno)) return;
        Member admin = new Member();
        admin.setRegno(adminRegno);
        admin.setFullName("System Administrator");
        admin.setStatus(MemberStatus.ACTIVE);
        admin.setRole(MemberRole.ADMIN);
        admin.setMonthlySavingsAmount(20000);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setMustChangePassword(true);
        memberRepository.save(admin);
    }
}
