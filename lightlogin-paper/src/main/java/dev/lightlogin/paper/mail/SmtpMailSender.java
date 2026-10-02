package dev.lightlogin.paper.mail;

import dev.lightlogin.core.config.MailConfig;
import dev.lightlogin.core.mail.MailException;
import dev.lightlogin.core.mail.MailMessage;
import dev.lightlogin.core.mail.MailSender;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * SMTP adapter for password recovery.
 *
 * <p>Opens a connection per message rather than holding one open: recovery mail is rare, and a
 * long-lived authenticated SMTP connection is a liability on a game server that may sit idle for
 * hours. Every operation blocks and is therefore only ever called from the async executor.</p>
 */
public final class SmtpMailSender implements MailSender {

    private final MailConfig config;
    private final String password;

    /**
     * @param config            SMTP settings
     * @param decryptedPassword the SMTP password (already decrypted from configuration)
     */
    public SmtpMailSender(MailConfig config, String decryptedPassword) {
        this.config = config;
        this.password = decryptedPassword == null ? "" : decryptedPassword;
    }

    @Override
    public boolean isEnabled() {
        return config.enabled() && !config.host().isBlank() && !config.account().isBlank();
    }

    @Override
    public void send(MailMessage message) throws MailException {
        if (!isEnabled()) {
            throw new MailException("SMTP is not configured");
        }
        try {
            Session session = Session.getInstance(properties(), new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.account(), password);
                }
            });
            MimeMessage mime = new MimeMessage(session);
            mime.setFrom(new InternetAddress(config.account(), config.senderName()));
            mime.setRecipients(Message.RecipientType.TO, InternetAddress.parse(message.to(), false));
            mime.setSubject(message.subject());
            mime.setText(message.body(), "UTF-8");
            Transport.send(mime);
        } catch (Exception e) {
            throw new MailException("SMTP delivery failed: " + e.getMessage(), e);
        }
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", config.host());
        properties.put("mail.smtp.port", String.valueOf(config.port()));
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.timeout", String.valueOf(config.connectionTimeoutMillis()));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(config.connectionTimeoutMillis()));
        properties.put("mail.smtp.writetimeout", String.valueOf(config.connectionTimeoutMillis()));
        if (config.useTls()) {
            // STARTTLS on the submission port; require it so a downgrade is not possible.
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        }
        properties.put("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
        return properties;
    }
}