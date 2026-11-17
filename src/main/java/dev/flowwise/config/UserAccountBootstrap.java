package dev.flowwise.config;

import dev.flowwise.domain.UserAccount;
import dev.flowwise.repository.UserAccountRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserAccountBootstrap implements ApplicationRunner {
    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;

    public UserAccountBootstrap(UserAccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing("admin", "admin123", "ADMIN,MANAGER,MEMBER");
        createIfMissing("manager", "manager123", "MANAGER,MEMBER");
        createIfMissing("member", "member123", "MEMBER");
    }

    private void createIfMissing(String username, String password, String roles) {
        if (!accounts.existsByUsernameIgnoreCase(username)) {
            accounts.save(new UserAccount(username, encoder.encode(password), roles, "ACTIVE", null));
        }
    }
}
