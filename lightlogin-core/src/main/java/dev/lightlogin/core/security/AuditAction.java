package dev.lightlogin.core.security;

/**
 * Stable audit action codes.
 *
 * <p>Using constants rather than ad-hoc strings means the audit trail is queryable and the web
 * panel can map actions to icons and filters without string matching in the presentation layer.</p>
 */
public final class AuditAction {

    private AuditAction() {
    }

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGIN_BLOCKED = "LOGIN_BLOCKED";
    public static final String LOGOUT = "LOGOUT";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_FAILURE = "REGISTER_FAILURE";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String ACCOUNT_UNREGISTERED = "ACCOUNT_UNREGISTERED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String CAPTCHA_FAILURE = "CAPTCHA_FAILURE";
    public static final String CAPTCHA_SUCCESS = "CAPTCHA_SUCCESS";
    public static final String IP_BANNED = "IP_BANNED";
    public static final String IP_UNBANNED = "IP_UNBANNED";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String CONNECTION_THROTTLED = "CONNECTION_THROTTLED";
    public static final String COUNTRY_BLOCKED = "COUNTRY_BLOCKED";
    public static final String ADMIN_PANEL_LOGIN = "ADMIN_PANEL_LOGIN";
    public static final String ADMIN_PANEL_LOGIN_FAILURE = "ADMIN_PANEL_LOGIN_FAILURE";
    public static final String ADMIN_ACTION = "ADMIN_ACTION";
    public static final String SECURITY_VIOLATION = "SECURITY_VIOLATION";
    public static final String STARTUP = "STARTUP";
    public static final String SHUTDOWN = "SHUTDOWN";
}