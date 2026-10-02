package dev.lightlogin.core.model;

/**
 * The lifecycle state of an account.
 *
 * <p>Kept as an explicit state rather than a boolean so a locked account, a deleted-but-retained
 * account and an active account are distinguishable, and so a future state cannot silently fall
 * into "active".</p>
 */
public enum AccountStatus {
    /** Normal, usable account. */
    ACTIVE,
    /** Temporarily locked after too many failed attempts. */
    LOCKED,
    /** Marked for deletion but retained for audit/retention. */
    DISABLED
}