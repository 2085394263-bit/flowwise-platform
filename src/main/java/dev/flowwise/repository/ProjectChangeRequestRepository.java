package dev.flowwise.repository;

import dev.flowwise.domain.ProjectChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectChangeRequestRepository extends JpaRepository<ProjectChangeRequest, Long> {
    List<ProjectChangeRequest> findByStatusOrderByCreatedAtDesc(String status);
    boolean existsByProjectIdAndStatus(Long projectId, String status);
    long countByStatus(String status);
}
