package dev.lightlogin.core.support;

import dev.lightlogin.core.mail.MailException;
import dev.lightlogin.core.mail.MailMessage;
import dev.lightlogin.core.mail.MailSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A recording {@link MailSender} for tests. */
public final class RecordingMailSender implements MailSender {

    private final List<MailMessage> sent = new CopyOnWriteArrayList<>();
    private boolean enabled = true;
    private boolean failNext;

    @Override
    public void send(MailMessage message) throws MailException {
        if (failNext) {
            failNext = false;
            throw new MailException("simulated delivery failure");
        }
        sent.add(message);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void failNextSend() {
        this.failNext = true;
    }

    public List<MailMessage> sent() {
        return List.copyOf(sent);
    }

    public MailMessage last() {
        return sent.isEmpty() ? null : sent.get(sent.size() - 1);
    }
}