package dev.flowwise.api;

import dev.flowwise.domain.Project;
import dev.flowwise.domain.ProjectChangeRequest;
import dev.flowwise.domain.WorkTask;
import dev.flowwise.service.FlowWiseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Validated
public class ProjectController {
    private final FlowWiseService service;
    public ProjectController(FlowWiseService service) { this.service = service; }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(Authentication auth) { return service.dashboard(auth); }

    @GetMapping("/projects")
    public List<Map<String, Object>> projects(Authentication auth) { return service.projectCards(auth); }

    @PostMapping("/projects")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProjectChangeRequest createProject(@Valid @RequestBody ApiModels.ProjectInput input, Authentication auth) {
        return service.createProject(input, auth.getName());
    }

    @PutMapping("/projects/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProjectChangeRequest updateProject(@PathVariable Long projectId, @Valid @RequestBody ApiModels.ProjectInput input, Authentication auth) {
        return service.requestProjectUpdate(projectId, input, auth);
    }

    @DeleteMapping("/projects/{projectId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ProjectChangeRequest deleteProject(@PathVariable Long projectId, Authentication auth) {
        return service.requestProjectDelete(projectId, auth);
    }

    @GetMapping("/projects/{projectId}/tasks")
    public List<WorkTask> tasks(@PathVariable Long projectId, Authentication auth) { return service.projectTasks(projectId, auth); }

    @PostMapping("/projects/{projectId}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WorkTask createTask(@PathVariable Long projectId, @Valid @RequestBody ApiModels.TaskInput input, Authentication auth) {
        return service.createTask(projectId, input, auth);
    }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public WorkTask updateTask(@PathVariable Long taskId, @Valid @RequestBody ApiModels.TaskInput input, Authentication auth) {
        return service.updateTask(taskId, input, auth);
    }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void deleteTask(@PathVariable Long taskId, Authentication auth) {
        service.deleteTask(taskId, auth);
    }
}
