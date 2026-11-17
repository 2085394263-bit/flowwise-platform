package dev.flowwise.agent;

import dev.flowwise.service.FlowWiseService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
public class ProjectAgentTools {
    private final FlowWiseService flowWiseService;

    public ProjectAgentTools(FlowWiseService flowWiseService) {
        this.flowWiseService = flowWiseService;
    }

    @Tool(description = "查询当前登录用户有权限查看的项目和任务。可按任务标题、状态、负责人关键词筛选。只用于回答项目进展、任务分配、截止日期和待办情况。")
    public Map<String, Object> searchVisibleWork(
            @ToolParam(description = "可选关键词；查询全部可见工作时传空字符串") String query,
            ToolContext context) {
        String username = (String) context.getContext().get("username");
        @SuppressWarnings("unchecked")
        Collection<String> roles = (Collection<String>) context.getContext().get("roles");
        return flowWiseService.agentWorkSnapshot(username, roles, query);
    }
}
