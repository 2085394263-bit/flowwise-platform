package dev.flowwise.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fw_approval")
public class Approval {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long taskId;
    @Column(nullable = false, length = 80)
    private String requester;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(length = 500)
    private String comment;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    private LocalDateTime decidedAt;

    protected Approval() { }

    public Approval(Long taskId, String requester) {
        this.taskId = taskId;
        this.requester = requester;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public String getRequester() { return requester; }
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
