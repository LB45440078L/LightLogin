package dev.lightlogin.core.web;

import dev.lightlogin.core.model.Account;
import dev.lightlogin.core.model.AuditEntry;
import dev.lightlogin.core.model.IpBan;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * HTML rendering for the admin panel.
 *
 * <p>All interpolated values pass through {@link HttpSupport#escape(String)}. Templates are plain
 * Java text blocks rather than a template engine, which keeps the shaded jar small and removes any
 * chance of a template-injection class of bug.</p>
 */
final class Templates {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private Templates() {
    }

    static String page(String title, String body, String csrf) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s · LightLogin</title>
                  <style>
                    :root{--bg:#0f1420;--panel:#161d2d;--panel-2:#1b2436;--fg:#e6ecf5;--muted:#8a97ad;--accent:#5b8cff;--ok:#3ddc97;--bad:#ff6b6b;--warn:#ffcf5b;--line:#24304a}
                    *{box-sizing:border-box}
                    body{margin:0;font:14px/1.5 system-ui,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--fg)}
                    header{display:flex;align-items:center;gap:16px;padding:12px 20px;background:var(--panel);border-bottom:1px solid var(--line);position:sticky;top:0;z-index:5}
                    header b{color:var(--accent)}
                    nav a{color:var(--muted);text-decoration:none;margin-right:14px;position:relative;transition:color .15s ease}
                    nav a:hover{color:var(--fg)}
                    nav a:after{content:"";position:absolute;left:0;right:100%%;bottom:-4px;height:2px;background:var(--accent);transition:right .18s ease}
                    nav a:hover:after{right:0}
                    main{padding:20px;max-width:1100px;margin:0 auto}
                    .card{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:16px;margin-bottom:16px;animation:rise .3s ease both}
                    .card:nth-child(2){animation-delay:.05s}
                    .card:nth-child(3){animation-delay:.1s}
                    table{width:100%%;border-collapse:collapse}
                    th,td{text-align:left;padding:8px;border-bottom:1px solid var(--line);vertical-align:middle}
                    th{color:var(--muted);font-weight:600}
                    tbody tr{transition:background .12s ease}
                    tbody tr:hover{background:var(--panel-2)}
                    .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}
                    .stat{background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:14px;transition:transform .16s ease,border-color .16s ease}
                    .stat:hover{transform:translateY(-2px);border-color:var(--accent)}
                    .stat b{display:block;font-size:26px}
                    .muted{color:var(--muted)}
                    input,select,button{font:inherit;padding:8px 10px;border-radius:8px;border:1px solid #2c3a58;background:#0d1320;color:var(--fg);transition:border-color .15s ease,box-shadow .15s ease}
                    input:focus,select:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 3px rgba(91,140,255,.25)}
                    button{cursor:pointer;background:var(--accent);border-color:var(--accent);color:#06101f;font-weight:600;transition:filter .15s ease,transform .06s ease}
                    button:hover{filter:brightness(1.12)}
                    button:active{transform:translateY(1px) scale(.99)}
                    .bad{color:var(--bad)}.ok{color:var(--ok)}.warn{color:var(--warn)}
                    code{background:#0d1320;padding:2px 6px;border-radius:6px}
                    .row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
                    a{color:var(--accent);transition:opacity .15s ease}
                    a:hover{opacity:.82}
                    .who{display:inline-flex;align-items:center;gap:8px}
                    .head{display:inline-block;vertical-align:middle;border-radius:6px;image-rendering:pixelated;box-shadow:0 1px 0 rgba(0,0,0,.4);transition:transform .18s ease}
                    .head:hover{transform:rotate(-4deg) scale(1.12)}
                    @keyframes rise{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:none}}
                    @media (prefers-reduced-motion:reduce){*{animation:none!important;transition:none!important}}
                  </style>
                </head>
                <body>
                <header><b>LightLogin</b><nav>
                  <a href="/">Dashboard</a><a href="/players">Players</a>
                  <a href="/bans">Bans</a><a href="/logs">Audit log</a>
                </nav><span style="margin-left:auto"><a href="/logout">Sign out</a></span></header>
                <main>%s</main>
                <script>document.querySelectorAll('form.action').forEach(function(f){
                  f.addEventListener('submit',function(){ if(!f.dataset.confirm) return;
                    if(!confirm(f.dataset.confirm)){event.preventDefault();}});});</script>
                </body></html>
                """.formatted(HttpSupport.escape(title), body);
    }

    static String login(String error, String csrf) {
        String message = error == null ? "" : "<p class=\"bad\">" + HttpSupport.escape(error) + "</p>";
        return """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Sign in · LightLogin</title>
                <style>
                  body{margin:0;font:14px/1.5 system-ui,sans-serif;background:#0f1420;color:#e6ecf5;display:flex;min-height:100vh;align-items:center;justify-content:center}
                  form{background:#161d2d;border:1px solid #24304a;border-radius:12px;padding:24px;width:320px}
                  h1{font-size:18px;margin:0 0 16px}
                  input{width:100%%;padding:10px;margin-bottom:12px;border-radius:8px;border:1px solid #2c3a58;background:#0d1320;color:#e6ecf5}
                  button{width:100%%;padding:10px;border:0;border-radius:8px;background:#5b8cff;color:#06101f;font-weight:700;cursor:pointer}
                  .bad{color:#ff6b6b;margin:0 0 12px}
                </style></head><body>
                <form method="post" action="/login">
                  <h1>LightLogin administration</h1>
                  %s
                  <input type="hidden" name="csrf" value="%s">
                  <input name="username" placeholder="Username" autocomplete="username" required>
                  <input name="password" type="password" placeholder="Password" autocomplete="current-password" required>
                  <input name="totp" placeholder="2FA code (if enabled)" autocomplete="one-time-code" inputmode="numeric">
                  <button type="submit">Sign in</button>
                </form></body></html>
                """.formatted(message, HttpSupport.escape(csrf));
    }

    static String dashboard(AdminService.Stats stats, List<AuditEntry> recent) {
        StringBuilder rows = new StringBuilder();
        for (AuditEntry entry : recent) {
            rows.append("<tr><td class=\"muted\">").append(TIMESTAMP.format(Instant.ofEpochMilli(entry.timestampMillis())))
                    .append("</td><td>").append(HttpSupport.escape(entry.action()))
                    .append("</td><td>").append(HttpSupport.escape(entry.actor()))
                    .append("</td><td>").append(HttpSupport.escape(entry.subject()))
                    .append("</td><td class=\"muted\">").append(HttpSupport.escape(entry.ip()))
                    .append("</td></tr>");
        }
        return """
                <div class="grid">
                  <div class="stat"><span class="muted">Accounts</span><b>%d</b></div>
                  <div class="stat"><span class="muted">Active bans</span><b>%d</b></div>
                  <div class="stat"><span class="muted">Live sessions</span><b>%d</b></div>
                  <div class="stat"><span class="muted">Audit entries</span><b>%d</b></div>
                </div>
                <div class="card"><h2>Recent activity</h2>
                <table><thead><tr><th>Time</th><th>Action</th><th>Actor</th><th>Subject</th><th>IP</th></tr></thead>
                <tbody>%s</tbody></table></div>
                """.formatted(stats.accounts(), stats.activeBans(), stats.sessions(),
                stats.auditEntries(), rows.toString());
    }

    static String players(List<Account> accounts, int page, int pageSize, String query) {
        StringBuilder rows = new StringBuilder();
        for (Account account : accounts) {
            rows.append("<tr><td><span class=\"who\">").append(avatar(account.uuid(), account.username()))
                    .append(HttpSupport.escape(account.username())).append("</span>")
                    .append("</td><td class=\"muted\"><code>").append(HttpSupport.escape(account.uuid()))
                    .append("</code></td><td>").append(account.isRegistered() ? "yes" : "<span class=\"warn\">no</span>")
                    .append("</td><td>").append(HttpSupport.escape(account.email() == null ? "—" : account.email()))
                    .append("</td><td class=\"muted\">").append(HttpSupport.escape(account.lastIp() == null ? "—" : account.lastIp()))
                    .append("</td><td>").append(account.lastLoginMillis() == 0 ? "—"
                            : TIMESTAMP.format(Instant.ofEpochMilli(account.lastLoginMillis())))
                    .append("</td><td><a href=\"/player?uuid=").append(HttpSupport.escape(account.uuid()))
                    .append("\">Manage</a></td></tr>");
        }
        String search = "<form method=\"get\" action=\"/players\" class=\"row\">"
                + "<input name=\"q\" placeholder=\"Search username\" value=\"" + HttpSupport.escape(query == null ? "" : query) + "\">"
                + "<button type=\"submit\">Search</button></div></form>";
        String nav = "<div class=\"row\">"
                + (page > 0 ? "<a href=\"/players?page=" + (page - 1) + "\">← Previous</a>" : "")
                + "<span class=\"muted\">Page " + (page + 1) + "</span>"
                + (accounts.size() >= pageSize ? "<a href=\"/players?page=" + (page + 1) + "\">Next →</a>" : "")
                + "</div>";
        return """
                <div class="card"><h2>Players</h2>%s
                <table><thead><tr><th>Name</th><th>UUID</th><th>Registered</th><th>Email</th><th>Last IP</th><th>Last login</th><th></th></tr></thead>
                <tbody>%s</tbody></table>%s</div>
                """.formatted(search, rows.toString(), nav);
    }

    static String playerDetail(Account account, String csrf, List<AuditEntry> history, String flash) {
        StringBuilder rows = new StringBuilder();
        for (AuditEntry entry : history) {
            rows.append("<tr><td class=\"muted\">").append(TIMESTAMP.format(Instant.ofEpochMilli(entry.timestampMillis())))
                    .append("</td><td>").append(HttpSupport.escape(entry.action()))
                    .append("</td><td class=\"muted\">").append(HttpSupport.escape(entry.detail()))
                    .append("</td><td class=\"muted\">").append(HttpSupport.escape(entry.ip())).append("</td></tr>");
        }
        String flashHtml = flash == null ? "" : "<p class=\"ok\">" + HttpSupport.escape(flash) + "</p>";
        return """
                <div class="card">
                  <h2><span class="who">%s%s</span></h2>%s
                  <p class="muted">UUID <code>%s</code> · registered: %s · last IP: %s</p>
                  <div class="row">
                    <form class="action" method="post" action="/action" data-confirm="Reset this player's password?">
                      <input type="hidden" name="csrf" value="%s"><input type="hidden" name="action" value="reset">
                      <input type="hidden" name="uuid" value="%s"><button type="submit">Reset password</button></form>
                    <form class="action" method="post" action="/action" data-confirm="Ban the last IP of this player?">
                      <input type="hidden" name="csrf" value="%s"><input type="hidden" name="action" value="ban-last-ip">
                      <input type="hidden" name="uuid" value="%s"><button type="submit">Ban last IP</button></form>
                    <form class="action" method="post" action="/action" data-confirm="Unregister this account? This deletes the login record.">
                      <input type="hidden" name="csrf" value="%s"><input type="hidden" name="action" value="unregister">
                      <input type="hidden" name="uuid" value="%s"><button type="submit">Unregister</button></form>
                  </div>
                </div>
                <div class="card"><h2>History</h2>
                <table><thead><tr><th>Time</th><th>Action</th><th>Detail</th><th>IP</th></tr></thead>
                <tbody>%s</tbody></table></div>
                """.formatted(avatar(account.uuid(), account.username()),
                HttpSupport.escape(account.username()), flashHtml, HttpSupport.escape(account.uuid()),
                account.isRegistered() ? "yes" : "no", HttpSupport.escape(account.lastIp() == null ? "—" : account.lastIp()),
                HttpSupport.escape(csrf), HttpSupport.escape(account.uuid()),
                HttpSupport.escape(csrf), HttpSupport.escape(account.uuid()),
                HttpSupport.escape(csrf), HttpSupport.escape(account.uuid()),
                rows.toString());
    }

    static String bans(List<IpBan> bans, String csrf, String flash) {
        StringBuilder rows = new StringBuilder();
        for (IpBan ban : bans) {
            rows.append("<tr><td><code>").append(HttpSupport.escape(ban.target()))
                    .append("</code></td><td>").append(HttpSupport.escape(ban.reason()))
                    .append("</td><td class=\"muted\">").append(HttpSupport.escape(ban.actor()))
                    .append("</td><td>").append(ban.isPermanent() ? "permanent"
                            : TIMESTAMP.format(Instant.ofEpochMilli(ban.expiresAtMillis())))
                    .append("</td><td>").append(ban.source())
                    .append("</td><td><form class=\"action\" method=\"post\" action=\"/action\">"
                            + "<input type=\"hidden\" name=\"csrf\" value=\"" + HttpSupport.escape(csrf) + "\">"
                            + "<input type=\"hidden\" name=\"action\" value=\"unban\">"
                            + "<input type=\"hidden\" name=\"target\" value=\"" + HttpSupport.escape(ban.target()) + "\">"
                            + "<button type=\"submit\">Unban</button></form></td></tr>");
        }
        String flashHtml = flash == null ? "" : "<p class=\"ok\">" + HttpSupport.escape(flash) + "</p>";
        return """
                <div class="card"><h2>Network bans</h2>%s
                  <form method="post" action="/action" class="row">
                    <input type="hidden" name="csrf" value="%s">
                    <input type="hidden" name="action" value="ban">
                    <input name="target" placeholder="IP or CIDR (e.g. 203.0.113.0/24)" required>
                    <input name="reason" placeholder="Reason">
                    <select name="duration"><option value="0">Permanent</option><option value="3600000">1 hour</option>
                      <option value="86400000">1 day</option><option value="604800000">7 days</option></select>
                    <button type="submit">Ban</button>
                  </form>
                  <table><thead><tr><th>Target</th><th>Reason</th><th>By</th><th>Expires</th><th>Source</th><th></th></tr></thead>
                  <tbody>%s</tbody></table></div>
                """.formatted(flashHtml, HttpSupport.escape(csrf), rows.toString());
    }

    static String logs(List<AuditEntry> entries, int page, int pageSize) {
        StringBuilder rows = new StringBuilder();
        for (AuditEntry entry : entries) {
            rows.append("<tr><td class=\"muted\">").append(TIMESTAMP.format(Instant.ofEpochMilli(entry.timestampMillis())))
                    .append("</td><td>").append(HttpSupport.escape(entry.action()))
                    .append("</td><td>").append(HttpSupport.escape(entry.actor()))
                    .append("</td><td>").append(HttpSupport.escape(entry.subject()))
                    .append("</td><td class=\"muted\">").append(HttpSupport.escape(entry.detail()))
                    .append("</td><td class=\"muted\">").append(HttpSupport.escape(entry.ip())).append("</td></tr>");
        }
        String nav = "<div class=\"row\">"
                + (page > 0 ? "<a href=\"/logs?page=" + (page - 1) + "\">← Previous</a>" : "")
                + "<span class=\"muted\">Page " + (page + 1) + "</span>"
                + (entries.size() >= pageSize ? "<a href=\"/logs?page=" + (page + 1) + "\">Next →</a>" : "")
                + "</div>";
        return """
                <div class="card"><h2>Audit log</h2>
                <table><thead><tr><th>Time</th><th>Action</th><th>Actor</th><th>Subject</th><th>Detail</th><th>IP</th></tr></thead>
                <tbody>%s</tbody></table>%s</div>
                """.formatted(rows.toString(), nav);
    }

    /**
     * A square player-head icon, drawn as inline SVG.
     *
     * <p>Deliberately <em>not</em> fetched from a skin service. An admin panel that asks a third
     * party for every avatar it renders leaks which players are being investigated, how many, and
     * when — from a loopback-only page that is otherwise invisible to the outside world. The head is
     * generated locally from the UUID instead, so it is deterministic, works offline, needs no
     * configuration, and is identical for every viewer.</p>
     *
     * <p>The face is drawn on the 8x8 grid a Minecraft head uses, and the palette is a stable hash of
     * the account, so the same player always gets the same head and two accounts never share one.</p>
     *
     * @param uuid the account UUID; falls back to the name when absent
     * @param name the player name, used only for the accessible label
     */
    static String avatar(String uuid, String name) {
        long hash = fnv1a(uuid == null || uuid.isBlank() ? (name == null ? "player" : name) : uuid);
        int hue = (int) Math.floorMod(hash, 360);
        String skin = hsl(hue, 42, 64);
        String fringe = hsl(hue, 38, 46);
        String hair = hsl((hue + 30) % 360, 38, 30);
        String eyes = hsl((hue + 200) % 360, 45, 18);
        String label = HttpSupport.escape(name == null ? "player" : name);
        return "<svg class=\"head\" width=\"28\" height=\"28\" viewBox=\"0 0 8 8\" shape-rendering=\"crispEdges\""
                + " role=\"img\" aria-label=\"Head of " + label + "\">"
                + "<rect width=\"8\" height=\"2\" fill=\"" + hair + "\"/>"
                + "<rect y=\"2\" width=\"8\" height=\"1\" fill=\"" + fringe + "\"/>"
                + "<rect y=\"3\" width=\"8\" height=\"5\" fill=\"" + skin + "\"/>"
                + "<rect x=\"1\" y=\"4\" width=\"2\" height=\"1\" fill=\"" + eyes + "\"/>"
                + "<rect x=\"5\" y=\"4\" width=\"2\" height=\"1\" fill=\"" + eyes + "\"/>"
                + "<rect x=\"3\" y=\"6\" width=\"2\" height=\"1\" fill=\"" + fringe + "\"/>"
                + "</svg>";
    }

    /** FNV-1a, so a small change in the UUID gives a completely different palette. */
    private static long fnv1a(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /**
     * A hex colour from HSL.
     *
     * <p>Deriving every colour on the head from one hue keeps the palette coherent whatever the
     * account hashes to, which fixed lightness values alone would not.</p>
     */
    private static String hsl(int hue, int saturationPercent, int lightnessPercent) {
        float s = saturationPercent / 100f;
        float l = lightnessPercent / 100f;
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float h = hue / 60f;
        float x = c * (1 - Math.abs(h % 2 - 1));
        float m = l - c / 2;
        float r;
        float g;
        float b;
        switch ((int) h % 6) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return String.format("#%02x%02x%02x",
                Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    static String error(int status, String message) {
        return page("Error " + status, "<div class=\"card\"><h2 class=\"bad\">Error "
                + status + "</h2><p>" + HttpSupport.escape(message) + "</p></div>", "");
    }

    /** Exposed for tests. */
    static String formatTimestamp(long epochMillis) {
        return TIMESTAMP.format(Instant.ofEpochMilli(epochMillis));
    }

    /** Placeholder map retained for future localisation. */
    static Map<String, String> emptyModel() {
        return Map.of();
    }
}