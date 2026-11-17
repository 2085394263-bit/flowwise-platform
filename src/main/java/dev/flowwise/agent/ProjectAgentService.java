package dev.flowwise.agent;

import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

public class ProjectAgentService {
    private final ChatClient chatClient;
    private final ProjectAgentTools tools;

    public ProjectAgentService(ChatClient.Builder builder, ProjectAgentTools tools) {
        this.chatClient = builder.defaultSystem("你是 FlowWise 项目协作助手。回答项目和任务问题时，必须先调用 searchVisibleWork 获取当前用户可见数据；只依据工具返回的数据回答，不要猜测，不要声称执行了任何写操作。用简洁中文回答，必要时指出没有查到的数据。")
                .build();
        this.tools = tools;
    }

    public String chat(String username, List<String> roles, String message) {
        return chatClient.prompt()
                .user(message)
                .tools(tools)
                .toolContext(Map.of("username", username, "roles", roles))
                .call()
                .content();
    }
}
