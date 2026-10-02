package dev.lightlogin.persistence;

import dev.lightlogin.core.port.StorageException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Shared plumbing for JDBC repositories: connection handling and the single translation point from
 * {@link SQLException} to the domain's unchecked {@link StorageException}.
 *
 * <p>Keeping {@code java.sql} behind this base class is what allows the core's repository
 * interfaces to be free of JDBC types.</p>
 */
public abstract class JdbcSupport {

    /** A function that may throw {@link SQLException}. */
    @FunctionalInterface
    protected interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    /** A consumer that may throw {@link SQLException}. */
    @FunctionalInterface
    protected interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    private final java.util.function.Supplier<Connection> connectionSupplier;

    protected JdbcSupport(javax.sql.DataSource dataSource) {
        this.connectionSupplier = () -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new StorageException("Could not acquire a database connection", e);
            }
        };
    }

    /** Runs a query on a pooled connection. */
    protected <T> T query(String operation, SqlFunction<T> work) {
        try (Connection connection = connectionSupplier.get()) {
            return work.apply(connection);
        } catch (SQLException e) {
            throw new StorageException(operation + " failed: " + e.getMessage(), e);
        }
    }

    /** Runs a write inside a transaction, committing on success and rolling back on failure. */
    protected void transaction(String operation, SqlConsumer work) {
        transactionReturning(operation, connection -> {
            work.accept(connection);
            return null;
        });
    }

    /** Runs a write that returns a value inside a transaction. */
    protected <T> T transactionReturning(String operation, SqlFunction<T> work) {
        try (Connection connection = connectionSupplier.get()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);
                }
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new StorageException(operation + " failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (e instanceof StorageException storage) {
                throw storage;
            }
            throw new StorageException(operation + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Whether an exception is a constraint violation, identified by the standard SQLState class
     * {@code 23} rather than a driver-specific code.
     */
    protected static boolean isConstraintViolation(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("23");
    }
}