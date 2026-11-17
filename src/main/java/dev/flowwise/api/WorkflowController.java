package dev.flowwise.api;

import dev.flowwise.domain.Approval;
import dev.flowwise.service.FlowWiseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api")
@Validated
public class WorkflowController {
    private final FlowWiseService service;
    public WorkflowController(FlowWiseService service) { this.service = service; }

    @PostMapping("/tasks/{taskId}/start")
    public Object startTask(@PathVariable Long taskId, Authentication auth) {
        return service.startTask(taskId, auth);
    }

    @PostMapping("/tasks/{taskId}/delivery")
    public Approval submitDelivery(@PathVariable Long taskId, @Valid @RequestBody ApiModels.TaskDeliveryInput input, Authentication auth) {
        return service.submitDelivery(taskId, input, auth);
    }

    @PostMapping("/tasks/{taskId}/submit")
    public ResponseEntity<Map<String, String>> legacySubmit() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Collections.singletonMap(
                "message", "任务提交方式已更新，请刷新页面后填写交付说明或链接，再提交验收"));
    }

    @GetMapping("/approvals/pending")
    public List<Map<String, Object>> pending(Authentication auth) { return service.pendingApprovals(auth); }

    @GetMapping("/project-requests/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> pendingProjectChanges() { return service.pendingProjectChanges(); }

    @PostMapping("/project-requests/{requestId}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public Object decideProjectChange(@PathVariable Long requestId, @Valid @RequestBody ApiModels.DecisionInput input, Authentication auth) {
        return service.decideProjectChange(requestId, input, auth.getName());
    }

    @PostMapping("/approvals/{approvalId}/decision")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Approval decide(@PathVariable Long approvalId, @Valid @RequestBody ApiModels.DecisionInput input, Authentication auth) {
        return service.decide(approvalId, input, auth);
    }

    @GetMapping("/activity")
    public Object activity() { return service.recentActivity(); }
}
