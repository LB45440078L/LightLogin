package dev.lightlogin.persistence;

import dev.lightlogin.core.port.StorageException;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Forward-only, additive schema migration runner.
 *
 * <p>Migrations are listed in an explicit {@code index.txt} read from the classpath, because
 * directory scanning silently finds nothing inside a packaged jar and would apply <em>no</em>
 * migrations in production. Each migration and its ledger row commit in one transaction, so an
 * interrupted upgrade leaves a consistent version rather than a half-migrated database.</p>
 */
public final class SchemaMigrator {

    private static final String INDEX = "/db/migrations/index.txt";
    private static final String LEDGER = """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version    VARCHAR(64) NOT NULL PRIMARY KEY,
                applied_at BIGINT      NOT NULL
            )
            """;

    private final DataSource dataSource;

    public SchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Applies every pending migration.
     *
     * @return the versions applied by this call
     */
    public List<String> migrate() {
        List<String> applied = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            ensureLedger(connection);
            Set<String> alreadyApplied = appliedVersions(connection);
            for (String migration : listMigrations()) {
                if (alreadyApplied.contains(migration)) {
                    continue;
                }
                applyMigration(connection, migration);
                applied.add(migration);
            }
        } catch (SQLException e) {
            throw new StorageException("Schema migration failed: " + e.getMessage(), e);
        }
        return List.copyOf(applied);
    }

    private void ensureLedger(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(LEDGER);
        }
    }

    private Set<String> appliedVersions(Connection connection) throws SQLException {
        Set<String> versions = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version FROM schema_migrations")) {
            while (rows.next()) {
                versions.add(rows.getString(1));
            }
        }
        return versions;
    }

    private void applyMigration(Connection connection, String migration) throws SQLException {
        String sql = readResource("/db/migrations/" + migration);
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (String statement : splitStatements(sql)) {
                try (Statement s = connection.createStatement()) {
                    s.execute(statement);
                }
            }
            try (var ps = connection.prepareStatement(
                    "INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)")) {
                ps.setString(1, migration);
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private List<String> listMigrations() {
        List<String> migrations = new ArrayList<>();
        for (String line : readResource(INDEX).split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                migrations.add(trimmed);
            }
        }
        return migrations;
    }

    /** Splits a script into statements on semicolons, ignoring comments and blank lines. */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                String statement = current.toString().trim();
                statements.add(statement.substring(0, statement.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }

    private static String readResource(String path) {
        try (InputStream in = SchemaMigrator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new StorageException("Migration resource not found on the classpath: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        } catch (IOException e) {
            throw new StorageException("Could not read migration resource: " + path, e);
        }
    }
}