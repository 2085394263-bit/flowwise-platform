package dev.flowwise.service;

import dev.flowwise.domain.UserAccount;
import dev.flowwise.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository accounts;

    public DatabaseUserDetailsService(UserAccountRepository accounts) { this.accounts = accounts; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = accounts.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("账号或密码不正确"));
        String[] roles = Arrays.stream(account.getRoles().split(","))
                .filter(role -> !role.trim().isEmpty()).map(String::trim).toArray(String[]::new);
        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles(roles)
                .disabled(!"ACTIVE".equals(account.getStatus()))
                .build();
    }
}
