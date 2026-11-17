package dev.flowwise.service;

import dev.flowwise.domain.UserAccount;
import dev.flowwise.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserAccountService {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,32}$");
    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;

    public UserAccountService(UserAccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Transactional
    public Map<String, Object> register(String usernameInput, String password, String roleInput) {
        String username = normalizeUsername(usernameInput);
        validatePassword(password);
        String requestedRole = roleInput == null ? "MEMBER" : roleInput.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("MEMBER", "MANAGER").contains(requestedRole)) {
            throw new IllegalArgumentException("公开注册仅支持成员注册或项目经理申请；管理员账号由管理员创建");
        }
        boolean managerRequest = "MANAGER".equals(requestedRole);
        String status = managerRequest ? "PENDING" : "ACTIVE";
        String roles = managerRequest ? "MEMBER" : "MEMBER";
        UserAccount account = accounts.findByUsernameIgnoreCase(username).orElse(null);
        if (account != null) {
            if (!"REJECTED".equals(account.getStatus())) throw new IllegalStateException("账号名已被使用");
            account.retryRegistration(encoder.encode(password), managerRequest ? "MANAGER" : null, roles, status);
        } else {
            account = accounts.save(new UserAccount(username, encoder.encode(password), roles, status, managerRequest ? "MANAGER" : null));
        }
        return safeAccount(account, managerRequest ? "项目经理申请已提交，管理员批准后即可登录" : "注册成功，现在可以登录");
    }

    @Transactional
    public Map<String, Object> createByAdmin(String usernameInput, String password, String roleInput) {
        String username = normalizeUsername(usernameInput);
        validatePassword(password);
        String role = roleInput == null ? "MEMBER" : roleInput.trim().toUpperCase(Locale.ROOT);
        String roles = rolesFor(role);
        if (accounts.existsByUsernameIgnoreCase(username)) throw new IllegalStateException("账号名已被使用");
        UserAccount account = accounts.save(new UserAccount(username, encoder.encode(password), roles, "ACTIVE", null));
        return safeAccount(account, "账号已创建，可使用新账号登录");
    }

    public List<Map<String, Object>> pendingManagerRegistrations() {
        return accounts.findByStatusAndRequestedRoleOrderByCreatedAtAsc("PENDING", "MANAGER")
                .stream().map(account -> safeAccount(account, null)).collect(Collectors.toList());
    }

    public List<Map<String, Object>> assignableMembers() {
        return accounts.findAll().stream()
                .filter(account -> "ACTIVE".equals(account.getStatus()))
                .filter(account -> Arrays.asList(account.getRoles().split(",")).contains("MEMBER"))
                .filter(account -> !Arrays.asList(account.getRoles().split(",")).contains("ADMIN"))
                .filter(account -> !Arrays.asList(account.getRoles().split(",")).contains("MANAGER"))
                .sorted(Comparator.comparing(UserAccount::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(account -> {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("username", account.getUsername());
                    return item;
                }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> decideManagerRegistration(Long accountId, boolean approved, String comment) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("注册申请不存在"));
        if (!"PENDING".equals(account.getStatus()) || !"MANAGER".equals(account.getRequestedRole())) {
            throw new IllegalStateException("该注册申请已处理");
        }
        account.decideManagerRequest(approved, comment);
        return safeAccount(account, approved ? "项目经理账号已启用" : "项目经理申请已驳回");
    }

    private Map<String, Object> safeAccount(UserAccount account, String message) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", account.getId());
        result.put("username", account.getUsername());
        result.put("status", account.getStatus());
        result.put("roles", account.getRoles());
        result.put("requestedRole", account.getRequestedRole());
        result.put("reviewComment", account.getReviewComment());
        result.put("createdAt", account.getCreatedAt());
        if (message != null) result.put("message", message);
        return result;
    }

    private String rolesFor(String role) {
        if ("ADMIN".equals(role)) return "ADMIN,MANAGER,MEMBER";
        if ("MANAGER".equals(role)) return "MANAGER,MEMBER";
        if ("MEMBER".equals(role)) return "MEMBER";
        throw new IllegalArgumentException("角色仅支持 ADMIN、MANAGER、MEMBER");
    }

    private String normalizeUsername(String value) {
        if (value == null) throw new IllegalArgumentException("请输入账号名");
        String username = value.trim();
        if (!USERNAME.matcher(username).matches()) throw new IllegalArgumentException("账号名需为 3–32 位字母、数字或 . _ -");
        return username.toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("密码长度需为 8–72 位");
        }
    }
}
