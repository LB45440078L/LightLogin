package dev.lightlogin.core.support;

import dev.lightlogin.core.model.AuditEntry;
import dev.lightlogin.core.port.AuditRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** An in-memory {@link AuditRepository} for tests. */
public final class InMemoryAuditRepository implements AuditRepository {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public void append(AuditEntry entry) {
        entries.add(new AuditEntry(sequence.incrementAndGet(), entry.timestampMillis(), entry.actor(),
                entry.action(), entry.subject(), entry.detail(), entry.ip()));
    }

    @Override
    public List<AuditEntry> recent(int limit) {
        return page(0, limit);
    }

    @Override
    public List<AuditEntry> page(int offset, int limit) {
        List<AuditEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(AuditEntry::id).reversed());
        int from = Math.min(Math.max(0, offset), sorted.size());
        int to = Math.min(from + Math.max(0, limit), sorted.size());
        return List.copyOf(sorted.subList(from, to));
    }

    @Override
    public List<AuditEntry> forSubject(String subject, int limit) {
        List<AuditEntry> out = new ArrayList<>();
        for (AuditEntry entry : entries) {
            if (entry.subject().equals(subject)) {
                out.add(entry);
            }
        }
        out.sort(Comparator.comparingLong(AuditEntry::id).reversed());
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : List.copyOf(out);
    }

    @Override
    public long count() {
        return entries.size();
    }

    @Override
    public int purgeOlderThan(long beforeMillis) {
        int before = entries.size();
        entries.removeIf(e -> e.timestampMillis() < beforeMillis);
        return before - entries.size();
    }

    /** All recorded actions, in insertion order. */
    public List<String> actions() {
        return entries.stream().map(AuditEntry::action).toList();
    }

    public List<AuditEntry> all() {
        return List.copyOf(entries);
    }
}