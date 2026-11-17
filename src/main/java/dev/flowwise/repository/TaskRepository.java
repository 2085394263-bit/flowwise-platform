package dev.flowwise.repository;

import dev.flowwise.domain.WorkTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TaskRepository extends JpaRepository<WorkTask, Long> {
    List<WorkTask> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<WorkTask> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
    long countByProjectIdAndStatus(Long projectId, String status);
}
