package dev.lightlogin.core.crypto;

import java.util.Objects;
import java.util.function.Function;

/**
 * Owns a password across an asynchronous boundary.
 *
 * <p>Passing a password to another thread has an ordering hazard that is easy to get wrong and
 * almost impossible to see: if the caller wipes its {@code char[]} after handing the work off, the
 * worker may read the array <em>after</em> it was cleared and hash an empty password instead. The
 * failure is silent — the value is still valid input to the hash function — so it shows up as
 * "registration succeeded but the password never works", and only some of the time.</p>
 *
 * <p>This type removes the hazard by making the handoff explicit: {@link #claim(char[])} copies the
 * password and wipes the caller's array <em>on the calling thread</em>, so there is no window in
 * which the value can be lost, and {@link #use(Function)} runs the work and wipes the private copy
 * afterwards whatever happens.</p>
 *
 * <p>Not thread-safe by design: the instance is created on the submitting thread and consumed by
 * exactly one worker.</p>
 */
public final class OwnedPassword {

    private final char[] value;

    private OwnedPassword(char[] value) {
        this.value = value;
    }

    /**
     * Takes ownership of a password. Must be called on the thread that produced {@code source},
     * before the work is submitted to an executor.
     *
     * @param source the caller's array; it is wiped and must not be used again
     * @return the owned value
     */
    public static OwnedPassword claim(char[] source) {
        Objects.requireNonNull(source, "source");
        char[] copy = ConstantTime.copy(source);
        ConstantTime.wipe(source);
        return new OwnedPassword(copy);
    }

    /**
     * Runs work with the password and wipes it afterwards, whatever the work does.
     *
     * @param work receives the password; must not retain it
     */
    public <T> T use(Function<char[], T> work) {
        Objects.requireNonNull(work, "work");
        try {
            return work.apply(value);
        } finally {
            ConstantTime.wipe(value);
        }
    }

    /** Wipes without using the value, for a path that decides not to proceed. */
    public void destroy() {
        ConstantTime.wipe(value);
    }

    /** Never render the password, not even by accident in a log line. */
    @Override
    public String toString() {
        return "OwnedPassword[redacted]";
    }
}