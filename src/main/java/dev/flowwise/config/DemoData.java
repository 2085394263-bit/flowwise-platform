package dev.flowwise.config;

import dev.flowwise.domain.*;
import dev.flowwise.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class DemoData implements ApplicationRunner {
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final ApprovalRepository approvals;
    private final ActivityRepository activities;

    public DemoData(ProjectRepository projects, TaskRepository tasks, ApprovalRepository approvals, ActivityRepository activities) {
        this.projects = projects;
        this.tasks = tasks;
        this.approvals = approvals;
        this.activities = activities;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (projects.count() > 0) return;
        Project project = projects.save(new Project("团队协作工作台", "项目进度、任务分工与审批流程统一管理。", "manager", "ACTIVE"));
        WorkTask api = tasks.save(new WorkTask(project.getId(), "完成项目与任务 API", "member", "HIGH", LocalDate.now().plusDays(2)));
        api.setStatus("DONE");
        tasks.save(new WorkTask(project.getId(), "整理角色权限矩阵", "manager", "NORMAL", LocalDate.now().plusDays(4)));
        WorkTask ui = tasks.save(new WorkTask(project.getId(), "完善工作台交互", "member", "NORMAL", LocalDate.now().plusDays(5)));
        ui.setStatus("REVIEW");
        approvals.save(new Approval(ui.getId(), "member"));
        activities.save(new Activity("admin", "DEMO_READY", "FlowWise 演示数据已初始化"));
    }
}
