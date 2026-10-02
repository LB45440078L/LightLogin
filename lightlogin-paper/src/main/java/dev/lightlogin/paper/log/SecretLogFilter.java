package dev.lightlogin.paper.log;

import dev.lightlogin.core.security.SecretRedactor;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.message.Message;

import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * A Log4j2 filter that <em>drops</em> log lines containing credentials.
 *
 * <p>Cancelling a command event does not stop the server from logging the raw command line, so a
 * filter at the logger is the only place a password typed into chat or a command can actually be
 * kept out of the console and the log files. The filter denies rather than rewrites because Log4j
 * filters cannot modify an event; denying is exactly the required behaviour — the line never
 * appears anywhere.</p>
 *
 * <p>The pattern is deliberately tight (a password-bearing command followed by an argument) so
 * ordinary lines are not swallowed.</p>
 */
@Plugin(name = "LightLoginSecretFilter", category = "Core", elementType = "filter")
public final class SecretLogFilter extends AbstractFilter {

    /**
     * The command a player or the console dispatched, as the server writes it to the log.
     *
     * <p>Anchored on the server's own wording ({@code issued server command:}) so it cannot match
     * an unrelated line that merely mentions a command name.</p>
     */
    private static final Pattern DISPATCHED_COMMAND = Pattern.compile(
            "(?i)issued server command:\\s*/?(?:login|register|changepassword|changepsw|resetpassword|verify|temppassword)\\s+\\S+");

    /**
     * A credential-bearing command typed directly at the console, which is logged on its own line.
     *
     * <p>Anchored at the start of the line. An unanchored pattern also swallowed other plugins'
     * lines such as "Registered /register command", which is why this is anchored.</p>
     */
    private static final Pattern LEADING_COMMAND = Pattern.compile(
            "(?i)^\\s*/?(?:login|register|changepassword|changepsw|resetpassword|verify|temppassword)\\s+\\S+");

    private final AtomicLong denied = new AtomicLong();

    public SecretLogFilter() {
        super(Result.NEUTRAL, Result.NEUTRAL);
    }

    @Override
    public Result filter(LogEvent event) {
        if (event == null) {
            return Result.NEUTRAL;
        }
        if (containsCredential(event.getMessage())) {
            denied.incrementAndGet();
            return Result.DENY;
        }
        return Result.NEUTRAL;
    }

    private static boolean containsCredential(Message message) {
        if (message == null) {
            return false;
        }
        String text = message.getFormattedMessage();
        if (text == null || text.isEmpty()) {
            return false;
        }
        return DISPATCHED_COMMAND.matcher(text).find()
                || LEADING_COMMAND.matcher(text).find();
    }

    /** Also filters the raw parameter form, which some log calls use instead of a Message. */
    @Override
    public Result filter(org.apache.logging.log4j.core.Logger logger, org.apache.logging.log4j.Level level,
                         Marker marker, Message msg, Throwable t) {
        return containsCredential(msg) ? deny() : Result.NEUTRAL;
    }

    @Override
    public Result filter(org.apache.logging.log4j.core.Logger logger, org.apache.logging.log4j.Level level,
                         Marker marker, Object msg, Throwable t) {
        return msg != null && containsCredential(new org.apache.logging.log4j.message.ObjectMessage(msg))
                ? deny() : Result.NEUTRAL;
    }

    @Override
    public Result filter(org.apache.logging.log4j.core.Logger logger, org.apache.logging.log4j.Level level,
                         Marker marker, String msg, Object... params) {
        return msg != null && containsCredential(new org.apache.logging.log4j.message.SimpleMessage(msg))
                ? deny() : Result.NEUTRAL;
    }

    private Result deny() {
        denied.incrementAndGet();
        return Result.DENY;
    }

    /** The number of lines dropped; surfaced as a metric so a silent filter is detectable. */
    public long deniedCount() {
        return denied.get();
    }

    /** Exposed for tests: whether a line would be dropped. */
    public static boolean wouldRedact(String line) {
        return line != null && (DISPATCHED_COMMAND.matcher(line).find()
                || LEADING_COMMAND.matcher(line).find());
    }

    /** A redactor configured like the filter, for the plugin's own logging paths. */
    public static SecretRedactor redactor() {
        return new SecretRedactor(true);
    }
}