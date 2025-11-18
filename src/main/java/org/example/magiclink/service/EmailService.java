 package org.example.magiclink.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.mail.from:noreply@example.com}")
    private String fromEmail;

    @Value("${app.mail.from-name:Magic Link}")
    private String fromName;

    @Value("${app.magic-link.token-expiry-minutes:15}")
    private int expiryMinutes;

    public void sendMagicLink(String toEmail, String magicLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            Context context = new Context();
            context.setVariable("magicLink", magicLink);
            context.setVariable("expiryMinutes", expiryMinutes);

            String html = templateEngine.process("email/magic-link", context);

            helper.setFrom(fromEmail, fromName);
            helper.setTo(toEmail);
            helper.setSubject("Your Magic Sign-in Link");
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Sent magic link to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send magic link to {}", toEmail, e);
            throw new RuntimeException(e);
        }
    }
}

