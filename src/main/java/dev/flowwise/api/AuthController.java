package dev.flowwise.api;

import dev.flowwise.service.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Validated
public class AuthController {
    private final UserAccountService accounts;

    public AuthController(UserAccountService accounts) { this.accounts = accounts; }

    @PostMapping("/api/auth/register")
    public Map<String, Object> register(@Valid @RequestBody ApiModels.RegistrationInput input) {
        return accounts.register(input.username, input.password, input.role);
    }

    @PostMapping("/api/admin/accounts")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> createAccount(@Valid @RequestBody ApiModels.RegistrationInput input) {
        return accounts.createByAdmin(input.username, input.password, input.role);
    }

    @GetMapping("/api/admin/manager-registrations/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> pendingManagerRegistrations() {
        return accounts.pendingManagerRegistrations();
    }

    @GetMapping("/api/team/members")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<Map<String, Object>> assignableMembers() {
        return accounts.assignableMembers();
    }

    @PostMapping("/api/admin/manager-registrations/{accountId}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> decideManagerRegistration(@PathVariable Long accountId, @Valid @RequestBody ApiModels.DecisionInput input) {
        return accounts.decideManagerRegistration(accountId, input.approved, input.comment);
    }

    @GetMapping("/api/auth/me")
    public Map<String, Object> currentUser(Authentication authentication) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("username", authentication.getName());
        result.put("roles", authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList()));
        return result;
    }
}
