package dev.flowwise.service;

import dev.flowwise.api.ApiModels;
import dev.flowwise.domain.*;
import dev.flowwise.repository.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FlowWiseService {
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final ApprovalRepository approvals;
    private final ProjectChangeRequestRepository projectChanges;
    private final ActivityRepository activities;
    private final UserAccountRepository accounts;

    public FlowWiseService(ProjectRepository projects, TaskRepository tasks,
                           ApprovalRepository approvals, ProjectChangeRequestRepository projectChanges,
                           ActivityRepository activities, UserAccountRepository accounts) {
        this.projects = projects;
        this.tasks = tasks;
        this.approvals = approvals;
        this.projectChanges = projectChanges;
        this.activities = activities;
        this.accounts = accounts;
    }

    public Map<String, Object> dashboard(Authentication auth) {
        List<Project> allProjects = visibleProjects(auth);
        List<Map<String, Object>> projectCards = allProjects.stream().map(project -> projectCard(project, auth)).collect(Collectors.toList());
        Set<Long> activeProjectIds = allProjects.stream().map(Project::getId).collect(Collectors.toSet());
        List<WorkTask> activeTasks = visibleTasks(auth).stream().filter(t -> activeProjectIds.contains(t.getProjectId())).collect(Collectors.toList());
        long totalTasks = activeTasks.size();
        long doneTasks = activeTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("projectCount", allProjects.size());
        data.put("activeProjects", allProjects.stream().filter(p -> "ACTIVE".equals(p.getStatus())).count());
        data.put("taskCount", totalTasks);
        data.put("doneTasks", doneTasks);
        long visiblePendingApprovals = hasRole(auth, "ADMIN")
                ? approvals.countByStatus("PENDING") + projectChanges.countByStatus("PENDING")
                : pendingApprovals(auth).size();
        data.put("pendingApprovals", visiblePendingApprovals);
        data.put("completionRate", totalTasks == 0 ? 0 : Math.round(doneTasks * 100.0 / totalTasks));
        data.put("projects", projectCards);
        data.put("activities", activities.findTop20ByOrderByCreatedAtDesc());
        return data;
    }

    public List<Map<String, Object>> projectCards(Authentication auth) {
        return visibleProjects(auth).stream().map(project -> projectCard(project, auth)).collect(Collectors.toList());
    }

    @Transactional
    public ProjectChangeRequest createProject(ApiModels.ProjectInput input, String actor) {
        String name = input.name.trim();
        ensureProjectNameAvailable(name, null);
        Project saved = projects.save(new Project(name, input.description, actor, "PENDING"));
        ProjectChangeRequest request = projectChanges.save(new ProjectChangeRequest(saved.getId(), "CREATE", actor, name, input.description));
        log(actor, "PROJECT_CREATE_REQUESTED", "申请创建项目「" + saved.getName() + "」，等待管理员审批");
        return request;
    }

    @Transactional
    public ProjectChangeRequest requestProjectUpdate(Long projectId, ApiModels.ProjectInput input, Authentication auth) {
        String actor = auth.getName();
        Project project = requireActiveProject(projectId);
        requireProjectManagerOrAdmin(project, auth);
        ensureNoPendingProjectChange(projectId);
        String name = input.name.trim();
        ensureProjectNameAvailable(name, projectId);
        if (project.getName().equals(name) && Objects.equals(project.getDescription(), input.description)) {
            throw new IllegalStateException("项目内容没有变化");
        }
        ProjectChangeRequest request = projectChanges.save(new ProjectChangeRequest(projectId, "UPDATE", actor, name, input.description));
        log(actor, "PROJECT_UPDATE_REQUESTED", "申请修改项目「" + project.getName() + "」，等待管理员审批");
        return request;
    }

    @Transactional
    public ProjectChangeRequest requestProjectDelete(Long projectId, Authentication auth) {
        String actor = auth.getName();
        Project project = requireActiveProject(projectId);
        requireProjectManagerOrAdmin(project, auth);
        ensureNoPendingProjectChange(projectId);
        ProjectChangeRequest request = projectChanges.save(new ProjectChangeRequest(projectId, "DELETE", actor, null, null));
        log(actor, "PROJECT_DELETE_REQUESTED", "申请删除项目「" + project.getName() + "」，等待管理员审批");
        return request;
    }

    public List<Map<String, Object>> pendingProjectChanges() {
        return projectChanges.findByStatusOrderByCreatedAtDesc("PENDING").stream().map(request -> {
            Project project = requireProjectAnyStatus(request.getProjectId());
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", request.getId());
            row.put("projectId", request.getProjectId());
            row.put("projectName", project.getName());
            row.put("action", request.getAction());
            row.put("requestedName", request.getRequestedName());
            row.put("requestedDescription", request.getRequestedDescription());
            row.put("requester", request.getRequester());
            row.put("createdAt", request.getCreatedAt());
            return row;
        }).collect(Collectors.toList());
    }

    @Transactional
    public ProjectChangeRequest decideProjectChange(Long requestId, ApiModels.DecisionInput input, String actor) {
        ProjectChangeRequest request = projectChanges.findById(requestId).orElseThrow(() -> new EntityNotFoundException("项目变更申请不存在"));
        if (!"PENDING".equals(request.getStatus())) throw new IllegalStateException("该项目申请已处理");
        Project project = requireProjectAnyStatus(request.getProjectId());
        if (input.approved) {
            if ("CREATE".equals(request.getAction())) {
                project.setStatus("ACTIVE");
            } else if ("UPDATE".equals(request.getAction())) {
                ensureProjectNameAvailable(request.getRequestedName(), project.getId());
                project.updateDetails(request.getRequestedName(), request.getRequestedDescription());
            } else if ("DELETE".equals(request.getAction())) {
                project.setStatus("ARCHIVED");
            }
        } else if ("CREATE".equals(request.getAction())) {
            project.setStatus("REJECTED");
        }
        request.decide(input.approved, input.comment);
        String verb = input.approved ? "批准" : "驳回";
        log(actor, "PROJECT_CHANGE_" + request.getStatus(), verb + "项目「" + project.getName() + "」的" + request.getAction() + "申请");
        return request;
    }

    public List<WorkTask> projectTasks(Long projectId, Authentication auth) {
        Project project = requireActiveProject(projectId);
        if (hasRole(auth, "ADMIN")) return tasks.findByProjectIdOrderByCreatedAtDesc(projectId);
        if (hasRole(auth, "MANAGER")) {
            requireProjectManagerOrAdmin(project, auth);
            return tasks.findByProjectIdOrderByCreatedAtDesc(projectId);
        }
        List<WorkTask> assigned = tasks.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(task -> auth.getName().equalsIgnoreCase(task.getAssignee())).collect(Collectors.toList());
        if (assigned.isEmpty()) throw new AccessDeniedException("你没有该项目的任务访问权限");
        return assigned;
    }

    @Transactional
    public WorkTask createTask(Long projectId, ApiModels.TaskInput input, Authentication auth) {
        String actor = auth.getName();
        Project project = requireActiveProject(projectId);
        requireProjectManagerOrAdmin(project, auth);
        validateAssignee(input.assignee);
        String priority = normalizedPriority(input.priority);
        WorkTask saved = tasks.save(new WorkTask(projectId, input.title.trim(), normalizedAssignee(input.assignee), priority, input.dueDate));
        log(actor, "TASK_CREATED", "在「" + project.getName() + "」创建任务「" + saved.getTitle() + "」");
        return saved;
    }

    @Transactional
    public WorkTask updateTask(Long taskId, ApiModels.TaskInput input, Authentication auth) {
        String actor = auth.getName();
        WorkTask task = requireTask(taskId);
        if ("REVIEW".equals(task.getStatus())) throw new IllegalStateException("任务审批处理中，暂时不能修改");
        Project project = requireActiveProject(task.getProjectId());
        requireProjectManagerOrAdmin(project, auth);
        validateAssignee(input.assignee);
        task.updateDetails(input.title.trim(), normalizedAssignee(input.assignee), normalizedPriority(input.priority), input.dueDate);
        log(actor, "TASK_UPDATED", "修改「" + project.getName() + "」中的任务「" + task.getTitle() + "」");
        return task;
    }

    @Transactional
    public void deleteTask(Long taskId, Authentication auth) {
        String actor = auth.getName();
        WorkTask task = requireTask(taskId);
        if (approvals.existsByTaskIdAndStatus(taskId, "PENDING")) throw new IllegalStateException("任务审批处理中，暂时不能删除");
        Project project = requireActiveProject(task.getProjectId());
        requireProjectManagerOrAdmin(project, auth);
        log(actor, "TASK_DELETED", "删除「" + project.getName() + "」中的任务「" + task.getTitle() + "」");
        tasks.delete(task);
    }

    @Transactional
    public WorkTask startTask(Long taskId, Authentication auth) {
        WorkTask task = requireTask(taskId);
        requireAssignee(task, auth);
        requireActiveProject(task.getProjectId());
        if (!"TODO".equals(task.getStatus())) throw new IllegalStateException("只有待开始任务可以开始执行");
        task.start();
        log(auth.getName(), "TASK_STARTED", "开始执行任务「" + task.getTitle() + "」");
        return task;
    }

    @Transactional
    public Approval submitDelivery(Long taskId, ApiModels.TaskDeliveryInput input, Authentication auth) {
        WorkTask task = requireTask(taskId);
        requireAssignee(task, auth);
        requireActiveProject(task.getProjectId());
        if (!"IN_PROGRESS".equals(task.getStatus())) throw new IllegalStateException("只有进行中的任务可以提交交付");
        String note = input.note == null ? "" : input.note.trim();
        String url = input.url == null ? "" : input.url.trim();
        if (note.isEmpty() && url.isEmpty()) throw new IllegalArgumentException("请填写交付说明或交付链接");
        if (!url.isEmpty() && !url.matches("https?://.+")) throw new IllegalArgumentException("交付链接需以 http:// 或 https:// 开头");
        task.submitDelivery(note.isEmpty() ? null : note, url.isEmpty() ? null : url);
        Approval saved = approvals.save(new Approval(taskId, auth.getName()));
        log(auth.getName(), "TASK_DELIVERY_SUBMITTED", "提交任务「" + task.getTitle() + "」交付验收");
        return saved;
    }

    public List<Map<String, Object>> pendingApprovals(Authentication auth) {
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "MANAGER")) return Collections.emptyList();
        return approvals.findByStatusOrderByCreatedAtDesc("PENDING").stream().filter(approval -> {
            WorkTask task = requireTask(approval.getTaskId());
            Project project = requireProject(task.getProjectId());
            return hasRole(auth, "ADMIN") || project.getOwner().equalsIgnoreCase(auth.getName());
        }).map(approval -> {
            WorkTask task = requireTask(approval.getTaskId());
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", approval.getId());
            row.put("taskId", task.getId());
            row.put("taskTitle", task.getTitle());
            row.put("projectName", requireProject(task.getProjectId()).getName());
            row.put("requester", approval.getRequester());
            row.put("deliveryNote", task.getDeliveryNote());
            row.put("deliveryUrl", task.getDeliveryUrl());
            row.put("createdAt", approval.getCreatedAt());
            return row;
        }).collect(Collectors.toList());
    }

    @Transactional
    public Approval decide(Long approvalId, ApiModels.DecisionInput input, Authentication auth) {
        String actor = auth.getName();
        Approval approval = approvals.findById(approvalId).orElseThrow(() -> new EntityNotFoundException("审批记录不存在"));
        if (!"PENDING".equals(approval.getStatus())) throw new IllegalStateException("该审批已处理");
        WorkTask task = requireTask(approval.getTaskId());
        Project project = requireActiveProject(task.getProjectId());
        requireProjectManagerOrAdmin(project, auth);
        approval.decide(input.approved, input.comment);
        if (input.approved) task.setStatus("DONE"); else task.rejectDelivery();
        log(actor, input.approved ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED",
                (input.approved ? "通过" : "驳回") + "任务「" + task.getTitle() + "」的审批");
        return approval;
    }

    public List<Activity> recentActivity() {
        return activities.findTop20ByOrderByCreatedAtDesc();
    }

    public Map<String, Object> agentWorkSnapshot(String username, Collection<String> roles, String query) {
        List<SimpleGrantedAuthority> authorities = roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        Authentication scopedAuth = new UsernamePasswordAuthenticationToken(username, "agent-context", authorities);
        Set<Long> visibleProjectIds = visibleProjects(scopedAuth).stream().map(Project::getId).collect(Collectors.toSet());
        String keyword = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<WorkTask> matchingTasks = visibleTasks(scopedAuth).stream()
                .filter(task -> visibleProjectIds.contains(task.getProjectId()))
                .filter(task -> keyword.isEmpty() || task.getTitle().toLowerCase(Locale.ROOT).contains(keyword)
                        || task.getStatus().toLowerCase(Locale.ROOT).contains(keyword)
                        || Optional.ofNullable(task.getAssignee()).orElse("").toLowerCase(Locale.ROOT).contains(keyword))
                .collect(Collectors.toList());
        List<Map<String, Object>> visibleWork = matchingTasks.stream().limit(100)
                .map(task -> {
                    Project project = requireProject(task.getProjectId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("project", project.getName());
                    row.put("task", task.getTitle());
                    row.put("assignee", task.getAssignee());
                    row.put("status", task.getStatus());
                    row.put("priority", task.getPriority());
                    row.put("dueDate", task.getDueDate());
                    return row;
                }).collect(Collectors.toList());
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("visibleProjectCount", visibleProjectIds.size());
        snapshot.put("matchingTaskCount", matchingTasks.size());
        snapshot.put("truncated", matchingTasks.size() > visibleWork.size());
        snapshot.put("tasks", visibleWork);
        return snapshot;
    }

    private Map<String, Object> projectCard(Project project, Authentication auth) {
        List<WorkTask> projectTasks = tasks.findByProjectIdOrderByCreatedAtDesc(project.getId());
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "MANAGER")) {
            projectTasks = projectTasks.stream().filter(task -> auth.getName().equalsIgnoreCase(task.getAssignee())).collect(Collectors.toList());
        }
        long count = projectTasks.size();
        long done = projectTasks.stream().filter(task -> "DONE".equals(task.getStatus())).count();
        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("id", project.getId());
        card.put("name", project.getName());
        card.put("description", project.getDescription());
        card.put("owner", project.getOwner());
        card.put("status", project.getStatus());
        card.put("taskCount", count);
        card.put("progress", count == 0 ? 0 : Math.round(done * 100.0 / count));
        card.put("createdAt", project.getCreatedAt());
        return card;
    }

    private Project requireProject(Long id) {
        return projects.findById(id).orElseThrow(() -> new EntityNotFoundException("项目不存在"));
    }

    private Project requireProjectAnyStatus(Long id) {
        return projects.findById(id).orElseThrow(() -> new EntityNotFoundException("项目不存在"));
    }

    private Project requireActiveProject(Long id) {
        Project project = requireProject(id);
        if (!"ACTIVE".equals(project.getStatus())) throw new IllegalStateException("项目尚未通过管理员审批或已归档");
        return project;
    }

    private void ensureNoPendingProjectChange(Long projectId) {
        if (projectChanges.existsByProjectIdAndStatus(projectId, "PENDING")) throw new IllegalStateException("该项目已有待管理员处理的申请");
    }

    private void ensureProjectNameAvailable(String name, Long exceptProjectId) {
        boolean taken = projects.findAll().stream().anyMatch(project ->
                !Objects.equals(project.getId(), exceptProjectId)
                        && Arrays.asList("ACTIVE", "PENDING").contains(project.getStatus())
                        && project.getName().equalsIgnoreCase(name));
        if (taken) throw new IllegalStateException("项目名称已存在，请进入现有项目或换一个名称");
    }

    private String normalizedPriority(String value) {
        String priority = value == null ? "NORMAL" : value.toUpperCase(Locale.ROOT);
        if (!Arrays.asList("LOW", "NORMAL", "HIGH").contains(priority)) throw new IllegalArgumentException("优先级仅支持 LOW、NORMAL、HIGH");
        return priority;
    }

    private WorkTask requireTask(Long id) {
        return tasks.findById(id).orElseThrow(() -> new EntityNotFoundException("任务不存在"));
    }

    private List<Project> visibleProjects(Authentication auth) {
        List<Project> active = projects.findAll().stream().filter(p -> "ACTIVE".equals(p.getStatus())).collect(Collectors.toList());
        if (hasRole(auth, "ADMIN")) return active;
        if (hasRole(auth, "MANAGER")) return active.stream().filter(p -> p.getOwner().equalsIgnoreCase(auth.getName())).collect(Collectors.toList());
        Set<Long> assignedProjectIds = visibleTasks(auth).stream().map(WorkTask::getProjectId).collect(Collectors.toSet());
        return active.stream().filter(p -> assignedProjectIds.contains(p.getId())).collect(Collectors.toList());
    }

    private List<WorkTask> visibleTasks(Authentication auth) {
        List<WorkTask> all = tasks.findAllByOrderByCreatedAtDesc();
        if (hasRole(auth, "ADMIN")) return all;
        if (hasRole(auth, "MANAGER")) return all.stream().filter(t -> {
            Project p = requireProject(t.getProjectId());
            return p.getOwner().equalsIgnoreCase(auth.getName());
        }).collect(Collectors.toList());
        return all.stream().filter(t -> auth.getName().equalsIgnoreCase(t.getAssignee())).collect(Collectors.toList());
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }

    private void requireProjectManagerOrAdmin(Project project, Authentication auth) {
        if (hasRole(auth, "ADMIN")) return;
        if (!hasRole(auth, "MANAGER") || !project.getOwner().equalsIgnoreCase(auth.getName())) {
            throw new AccessDeniedException("仅管理员或该项目负责人可以执行此操作");
        }
    }

    private void requireAssignee(WorkTask task, Authentication auth) {
        if (!auth.getName().equalsIgnoreCase(task.getAssignee())) throw new AccessDeniedException("只有任务负责人可以更新或提交该任务");
    }

    private void validateAssignee(String assignee) {
        if (assignee == null || assignee.trim().isEmpty()) return;
        UserAccount account = accounts.findByUsernameIgnoreCase(assignee.trim())
                .orElseThrow(() -> new IllegalArgumentException("任务负责人账号不存在"));
        if (!"ACTIVE".equals(account.getStatus())) throw new IllegalArgumentException("任务负责人账号尚未启用");
        if (!account.getRoles().contains("MEMBER") || account.getRoles().contains("ADMIN") || account.getRoles().contains("MANAGER")) {
            throw new IllegalArgumentException("任务只能分配给有效的成员员工账号");
        }
    }

    private String normalizedAssignee(String assignee) {
        return assignee == null || assignee.trim().isEmpty() ? null : assignee.trim();
    }

    private void log(String actor, String action, String detail) {
        activities.save(new Activity(actor, action, detail));
    }
}
