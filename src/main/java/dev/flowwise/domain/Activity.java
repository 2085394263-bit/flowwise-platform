package dev.flowwise.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fw_activity")
public class Activity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String actor;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(nullable = false, length = 240)
    private String detail;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Activity() { }

    public Activity(String actor, String action, String detail) {
        this.actor = actor;
        this.action = action;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
