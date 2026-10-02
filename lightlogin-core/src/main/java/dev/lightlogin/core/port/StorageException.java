package dev.lightlogin.core.port;

/**
 * Unchecked exception for storage failures.
 *
 * <p>Unchecked on purpose: every caller is an asynchronous worker whose only sensible response to a
 * storage failure is identical (log and fail the request), so forcing a checked exception onto the
 * domain interfaces would only produce boilerplate. JDBC's {@code SQLException} is translated into
 * this type in exactly one place, keeping {@code java.sql} out of the domain interfaces.</p>
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}