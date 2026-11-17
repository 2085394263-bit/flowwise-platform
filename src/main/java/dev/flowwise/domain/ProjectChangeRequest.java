package dev.flowwise.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fw_project_change_request")
public class ProjectChangeRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long projectId;
    @Column(nullable = false, length = 16)
    private String action;
    @Column(nullable = false, length = 80)
    private String requester;
    @Column(length = 100)
    private String requestedName;
    @Column(length = 500)
    private String requestedDescription;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(length = 500)
    private String comment;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;

    protected ProjectChangeRequest() { }

    public ProjectChangeRequest(Long projectId, String action, String requester, String name, String description) {
        this.projectId = projectId;
        this.action = action;
        this.requester = requester;
        this.requestedName = name;
        this.requestedDescription = description;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getAction() { return action; }
    public String getRequester() { return requester; }
    public String getRequestedName() { return requestedName; }
    public String getRequestedDescription() { return requestedDescription; }
    public String getStatus() { return status; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDecidedAt() { return decidedAt; }

    public void decide(boolean approved, String comment) {
        this.status = approved ? "APPROVED" : "REJECTED";
        this.comment = comment;
        this.decidedAt = LocalDateTime.now();
    }
}
