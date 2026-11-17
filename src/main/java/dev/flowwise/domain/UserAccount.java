package dev.flowwise.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fw_user_account")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 32)
    private String username;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 80)
    private String roles;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(length = 16)
    private String requestedRole;
    @Column(length = 500)
    private String reviewComment;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected UserAccount() { }

    public UserAccount(String username, String passwordHash, String roles, String status, String requestedRole) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.status = status;
        this.requestedRole = requestedRole;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRoles() { return roles; }
    public String getStatus() { return status; }
    public String getRequestedRole() { return requestedRole; }
    public String getReviewComment() { return reviewComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void retryRegistration(String passwordHash, String requestedRole, String roles, String status) {
        this.passwordHash = passwordHash;
        this.requestedRole = requestedRole;
        this.roles = roles;
        this.status = status;
        this.reviewComment = null;
        this.createdAt = LocalDateTime.now();
    }

    public void decideManagerRequest(boolean approved, String comment) {
        this.status = approved ? "ACTIVE" : "REJECTED";
        if (approved) this.roles = "MANAGER,MEMBER";
        this.requestedRole = null;
        this.reviewComment = comment;
    }
}
