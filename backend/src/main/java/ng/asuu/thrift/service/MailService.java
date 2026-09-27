package ng.asuu.thrift.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Wraps JavaMailSender so a missing/unreachable SMTP server never breaks a request - it just logs the message instead.
 *  Spring Boot only registers a JavaMailSender bean when spring.mail.host is actually set, so it's pulled in via
 *  ObjectProvider rather than a direct constructor dependency - a required JavaMailSender would fail application
 *  startup entirely whenever SMTP isn't configured, defeating the whole point of this fallback. */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final boolean configured;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider, @Value("${spring.mail.host:}") String host,
                        @Value("${thrift.mail.from:}") String from) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.configured = host != null && !host.isBlank() && this.mailSender != null;
        this.from = from;
    }

    public boolean isConfigured() { return configured; }

    public void sendPasswordReset(String toEmail, String fullName, String resetLink) {
        String subject = "Reset your ASUU-MOAUM Thrift password";
        String html = "<div style=\"font-family:Segoe UI,Roboto,Arial,sans-serif;max-width:480px;margin:0 auto\">"
                + "<h2 style=\"color:#6b1f2e\">ASUU-MOAUM Thrift</h2>"
                + "<p>Hi " + escape(fullName) + ",</p>"
                + "<p>We received a request to reset your Thrift &amp; Savings account password. Click the button below to choose a new one:</p>"
                + "<p style=\"text-align:center;margin:1.5rem 0\"><a href=\"" + resetLink + "\" style=\"background:#6b1f2e;color:#fff;padding:.75rem 1.5rem;border-radius:8px;text-decoration:none;font-weight:600\">Reset my password</a></p>"
                + "<p style=\"font-size:.85rem;color:#5f6b7a\">This link expires in 30 minutes. If you didn't request this, you can safely ignore this email - your password will not change.</p>"
                + "</div>";
        if (!configured) {
            log.warn("[mail] SMTP not configured — password reset link for {} <{}>: {}", fullName, toEmail, resetLink);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(from);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("[mail] Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
