package ng.asuu.thrift.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import ng.asuu.thrift.security.MemberPrincipal;
import ng.asuu.thrift.web.dto.LoginRequest;
import ng.asuu.thrift.web.dto.MemberView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<MemberView> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.regno(), req.password()));
        } catch (BadCredentialsException | org.springframework.security.authentication.LockedException e) {
            return ResponseEntity.status(401).build();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        MemberPrincipal principal = (MemberPrincipal) auth.getPrincipal();
        return ResponseEntity.ok(MemberView.of(principal.getMember()));
    }

    @GetMapping("/me")
    public ResponseEntity<MemberView> me(@AuthenticationPrincipal MemberPrincipal principal) {
        return ResponseEntity.ok(MemberView.of(principal.getMember()));
    }
}
