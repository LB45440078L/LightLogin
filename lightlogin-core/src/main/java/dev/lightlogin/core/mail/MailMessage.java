package dev.lightlogin.core.mail;

import java.util.Objects;

/**
 * An outbound email message.
 *
 * @param to      recipient address
 * @param subject subject line
 * @param body    plain-text body
 */
public record MailMessage(String to, String subject, String body) {

    public MailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }
}