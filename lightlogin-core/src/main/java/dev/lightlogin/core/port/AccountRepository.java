package dev.lightlogin.core.port;

import dev.lightlogin.core.model.Account;

import java.util.List;
import java.util.Optional;

/**
 * Storage port for player accounts.
 *
 * <p>Every method blocks; callers must invoke it off the server's main thread (the plugin routes
 * all of these through the async executor). There is deliberately no synchronous convenience method
 * that invites a main-thread call.</p>
 */
public interface AccountRepository {

    Optional<Account> findByUuid(String uuid);

    Optional<Account> findByUsername(String username);

    /** Inserts or replaces the account row. */
    void save(Account account);

    /** Updates only the password hash, clearing failure counters and any lock. */
    void updatePassword(String uuid, String passwordHash);

    /** Records a successful login (last IP, last login time, clears failures and lock). */
    void recordLogin(String uuid, String ip, long nowMillis);

    /** Atomically increments the failed-attempt counter and returns the new value. */
    int incrementFailedAttempts(String uuid);

    /** Clears the failed-attempt counter and any lock. */
    void resetFailedAttempts(String uuid);

    /** Locks the account until {@code untilMillis}. */
    void lock(String uuid, long untilMillis);

    /** Sets the recovery email. */
    void updateEmail(String uuid, String email);

    /** Deletes the account row. */
    void delete(String uuid);

    /** Number of accounts registered from an address (enforces per-IP registration limits). */
    long countByRegistrationIp(String ip);

    /** A page of accounts ordered by creation time, newest first. */
    List<Account> page(int offset, int limit);

    /** A page of accounts whose username contains {@code query} (case-insensitive). */
    List<Account> search(String query, int offset, int limit);

    long count();

    /** Number of accounts seen from an address in the last {@code sinceMillis}. */
    long countByLastIpSince(String ip, long sinceMillis);
}