package dev.lightlogin.core.mail;

/**
 * Port for sending email. Implemented by the SMTP adapter in the plugin module and by a fake in
 * tests, which keeps the recovery flow testable without a mail server.
 */
public interface MailSender {

    /**
     * Sends a message. Implementations block and must be invoked from the async executor.
     *
     * @throws MailException when delivery fails
     */
    void send(MailMessage message) throws MailException;

    /** Whether the sender is configured and able to attempt delivery. */
    boolean isEnabled();

    /** Releases any pooled resources. */
    default void close() {
    }
}