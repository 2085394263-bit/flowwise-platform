package dev.flowwise.repository;

import dev.flowwise.domain.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByStatusOrderByCreatedAtDesc(String status);
    boolean existsByTaskIdAndStatus(Long taskId, String status);
    long countByStatus(String status);
}
