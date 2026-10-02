package dev.lightlogin.core.config;

import java.util.List;

/**
 * SMTP settings for password recovery.
 *
 * @param enabled            master switch
 * @param host               SMTP host
 * @param port               SMTP port
 * @param useTls             whether to require a TLS connection
 * @param account            SMTP account / sender address
 * @param encryptedPassword  SMTP password (possibly {@code enc:}-encrypted)
 * @param senderName         display name on outgoing mail
 * @param subject            recovery-mail subject template
 * @param bodyTemplate       recovery-mail body lines ({@code {PLAYER}}, {@code {PASSWORD}})
 * @param recoveryLength     generated temporary-password length
 * @param cooldownMillis     minimum interval between resets for one account
 * @param connectionTimeoutMillis SMTP connection timeout
 */
public record MailConfig(
        boolean enabled,
        String host,
        int port,
        boolean useTls,
        String account,
        String encryptedPassword,
        String senderName,
        String subject,
        List<String> bodyTemplate,
        int recoveryLength,
        long cooldownMillis,
        long connectionTimeoutMillis) {

    public MailConfig {
        host = host == null ? "" : host;
        account = account == null ? "" : account;
        encryptedPassword = encryptedPassword == null ? "" : encryptedPassword;
        senderName = senderName == null || senderName.isBlank() ? "LightLogin" : senderName;
        subject = subject == null ? "Your new LightLogin password" : subject;
        bodyTemplate = List.copyOf(bodyTemplate == null ? List.of() : bodyTemplate);
        if (recoveryLength < 8) {
            recoveryLength = 12;
        }
        if (cooldownMillis < 0) {
            cooldownMillis = 90 * 60 * 1000L;
        }
        if (connectionTimeoutMillis <= 0) {
            connectionTimeoutMillis = 15_000;
        }
        if (port <= 0) {
            port = 587;
        }
    }

    public static MailConfig defaults() {
        return new MailConfig(false, "", 587, true, "", "", "LightLogin",
                "Your new LightLogin password",
                List.of("Dear {PLAYER},", "", "Your new temporary password is: {PASSWORD}",
                        "", "Please change it after logging in."),
                12, 90 * 60 * 1000L, 15_000);
    }
}