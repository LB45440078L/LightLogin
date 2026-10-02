package dev.lightlogin.core.model;

/**
 * Authorisation levels for the web panel.
 *
 * <p>Ordered by capability: a {@link #MODERATOR} can perform player-facing moderation but cannot
 * change server configuration or manage other admins; an {@link #ADMIN} has full control. The
 * ordinal ordering is used deliberately for "at least this role" checks.</p>
 */
public enum AdminRole {
    /** Read-only access to statistics and logs. */
    VIEWER(0),
    /** Can reset passwords, ban IPs and unregister players. */
    MODERATOR(1),
    /** Full control, including configuration and admin management. */
    ADMIN(2);

    private final int level;

    AdminRole(int level) {
        this.level = level;
    }

    /** Whether this role is at least as capable as {@code required}. */
    public boolean atLeast(AdminRole required) {
        return this.level >= required.level;
    }

    public int level() {
        return level;
    }
}