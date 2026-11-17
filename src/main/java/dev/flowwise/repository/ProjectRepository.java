package dev.flowwise.repository;

import dev.flowwise.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndStatusIn(String name, Collection<String> statuses);
}
