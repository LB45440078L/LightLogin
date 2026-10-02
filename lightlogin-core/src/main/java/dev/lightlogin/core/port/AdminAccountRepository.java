package dev.lightlogin.core.port;

import dev.lightlogin.core.model.AdminAccount;

import java.util.List;
import java.util.Optional;

/** Storage port for web-panel administrator accounts. */
public interface AdminAccountRepository {

    Optional<AdminAccount> findByUsername(String username);

    void save(AdminAccount account);

    void delete(String username);

    void recordLogin(String username, long nowMillis);

    List<AdminAccount> all();

    long count();
}