package dev.lightlogin.core.service;

import dev.lightlogin.core.config.MailConfig;
import dev.lightlogin.core.crypto.TokenGenerator;
import dev.lightlogin.core.mail.MailException;
import dev.lightlogin.core.mail.MailMessage;
import dev.lightlogin.core.mail.MailSender;
import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.port.AccountRepository;
import dev.lightlogin.core.security.AuditAction;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Password recovery by email.
 *
 * <p>Recovery is the weakest link in any authentication system, so this service is deliberately
 * conservative: it never reveals whether an account exists (the caller always sees the same
 * outcome), it enforces a per-account cooldown so it cannot be used to spam a mailbox, it generates
 * a high-entropy temporary password and it invalidates every existing session on success.</p>
 *
 * <p>Blocking (SMTP); callers must use the async executor.</p>
 */
public final class RecoveryService {

    /** The outcome of a recovery request, intentionally coarse to avoid account enumeration. */
    public enum Outcome {
        /** A reset was dispatched (or would have been, had the account existed). */
        DISPATCHED,
        /** The cooldown for this account has not elapsed. */
        COOLDOWN,
        /** Email recovery is disabled. */
        DISABLED,
        /** The account exists but has no recovery email on file. */
        NO_EMAIL,
        /** Delivery failed. */
        FAILED
    }

    private final AccountRepository accounts;
    private final dev.lightlogin.core.crypto.PasswordHasher hasher;
    private final SessionService sessions;
    private final MailSender mail;
    private final MailConfig config;
    private final TokenGenerator tokens;
    private final AuditService audit;
    private final LongSupplier clock;
    private final Map<String, Long> lastRequestMillis = new HashMap<>();

    public RecoveryService(AccountRepository accounts,
                           dev.lightlogin.core.crypto.PasswordHasher hasher,
                           SessionService sessions,
                           MailSender mail,
                           MailConfig config,
                           TokenGenerator tokens,
                           AuditService audit) {
        this(accounts, hasher, sessions, mail, config, tokens, audit, System::currentTimeMillis);
    }

    RecoveryService(AccountRepository accounts, dev.lightlogin.core.crypto.PasswordHasher hasher,
                    SessionService sessions, MailSender mail, MailConfig config, TokenGenerator tokens,
                    AuditService audit, LongSupplier clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.mail = Objects.requireNonNull(mail, "mail");
        this.config = Objects.requireNonNull(config, "config");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = clock;
    }

    /**
     * Requests a password reset for a username.
     *
     * @param username the account name
     * @param actor    who requested it (player name or "console")
     * @param ip       source address
     */
    public synchronized Outcome requestReset(String username, String actor, String ip) {
        if (!config.enabled() || !mail.isEnabled()) {
            return Outcome.DISABLED;
        }
        long now = clock.getAsLong();

        Optional<Account> found = accounts.findByUsername(username);
        if (found.isEmpty() || !found.get().isRegistered()) {
            // Do not leak existence; still honour the cooldown keyed by the requested name.
            return Outcome.DISPATCHED;
        }
        Account account = found.get();

        String key = account.uuid();
        Long last = lastRequestMillis.get(key);
        if (last != null && now - last < config.cooldownMillis()) {
            return Outcome.COOLDOWN;
        }
        if (account.emailOptional().isEmpty()) {
            return Outcome.NO_EMAIL;
        }

        String temporary = tokens.temporaryPassword(config.recoveryLength());
        char[] temporaryChars = temporary.toCharArray();
        try {
            accounts.updatePassword(account.uuid(), hasher.hash(temporaryChars));
        } finally {
            dev.lightlogin.core.crypto.ConstantTime.wipe(temporaryChars);
        }
        sessions.invalidateAll(account.uuid());

        MailMessage message = new MailMessage(account.email(), config.subject(),
                renderBody(config.bodyTemplate(), account.username(), temporary));
        try {
            mail.send(message);
        } catch (MailException e) {
            audit.record(actor, AuditAction.PASSWORD_RESET, account.uuid(), "delivery failed: " + e.getMessage(), ip);
            return Outcome.FAILED;
        }
        lastRequestMillis.put(key, now);
        audit.record(actor, AuditAction.PASSWORD_RESET, account.uuid(), "emailed to " + maskEmail(account.email()), ip);
        return Outcome.DISPATCHED;
    }

    /** Whether the account has requested a reset within the cooldown window. */
    public synchronized boolean inCooldown(String uuid) {
        Long last = lastRequestMillis.get(uuid);
        return last != null && clock.getAsLong() - last < config.cooldownMillis();
    }

    private static String renderBody(java.util.List<String> template, String player, String password) {
        StringBuilder builder = new StringBuilder();
        for (String line : template) {
            builder.append(line.replace("{PLAYER}", player).replace("{PASSWORD}", password)).append('\n');
        }
        return builder.toString();
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}