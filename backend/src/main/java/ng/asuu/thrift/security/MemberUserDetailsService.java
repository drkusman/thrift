package ng.asuu.thrift.security;

import ng.asuu.thrift.repo.MemberRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MemberUserDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

    public MemberUserDetailsService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String regno) throws UsernameNotFoundException {
        return memberRepository.findByRegno(regno)
                .map(MemberPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No member with regno " + regno));
    }
}
