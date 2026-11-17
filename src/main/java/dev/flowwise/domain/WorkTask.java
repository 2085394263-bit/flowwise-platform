package dev.flowwise.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fw_task")
public class WorkTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long projectId;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(length = 80)
    private String assignee;
    @Column(nullable = false, length = 16)
    private String priority;
    @Column(nullable = false, length = 24)
    private String status;
    private LocalDate dueDate;
    @Column(length = 2000)
    private String deliveryNote;
    @Column(length = 500)
    private String deliveryUrl;
    private LocalDateTime deliveredAt;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected WorkTask() { }

    public WorkTask(Long projectId, String title, String assignee, String priority, LocalDate dueDate) {
        this.projectId = projectId;
        this.title = title;
        this.assignee = assignee;
        this.priority = priority;
        this.status = "TODO";
        this.dueDate = dueDate;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getAssignee() { return assignee; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public LocalDate getDueDate() { return dueDate; }
    public String getDeliveryNote() { return deliveryNote; }
    public String getDeliveryUrl() { return deliveryUrl; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setStatus(String status) { this.status = status; }
    public void start() { this.status = "IN_PROGRESS"; }
    public void submitDelivery(String note, String url) {
        this.deliveryNote = note;
        this.deliveryUrl = url;
        this.deliveredAt = LocalDateTime.now();
        this.status = "REVIEW";
    }
    public void rejectDelivery() { this.status = "IN_PROGRESS"; }
    public void updateDetails(String title, String assignee, String priority, LocalDate dueDate) {
        this.title = title;
        this.assignee = assignee;
        this.priority = priority;
        this.dueDate = dueDate;
    }
}
