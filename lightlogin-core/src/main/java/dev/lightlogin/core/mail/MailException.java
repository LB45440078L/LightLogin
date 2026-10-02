package dev.lightlogin.core.mail;

/** Raised when an email cannot be delivered. */
public class MailException extends Exception {

    public MailException(String message) {
        super(message);
    }

    public MailException(String message, Throwable cause) {
        super(message, cause);
    }
}