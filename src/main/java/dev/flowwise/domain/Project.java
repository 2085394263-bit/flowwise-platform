package dev.flowwise.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fw_project")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(length = 500)
    private String description;
    @Column(nullable = false, length = 80)
    private String owner;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Project() { }

    public Project(String name, String description, String owner, String status) {
        this.name = name;
        this.description = description;
        this.owner = owner;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getOwner() { return owner; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setStatus(String status) { this.status = status; }
    public void updateDetails(String name, String description) { this.name = name; this.description = description; }
}
