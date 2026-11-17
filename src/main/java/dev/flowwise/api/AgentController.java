package dev.flowwise.api;

import dev.flowwise.agent.ProjectAgentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final ObjectProvider<ProjectAgentService> agentProvider;

    public AgentController(ObjectProvider<ProjectAgentService> agentProvider) {
        this.agentProvider = agentProvider;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@Valid @RequestBody ChatInput input, Authentication authentication) {
        ProjectAgentService agent = agentProvider.getIfAvailable();
        if (agent == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "message", "AI 助手尚未启用。请配置 SPRING_AI_MODEL_CHAT=openai 和 SPRING_AI_OPENAI_API_KEY 后重启服务。"));
        }
        String answer = agent.chat(authentication.getName(), authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).collect(Collectors.toList()), input.message.trim());
        Map<String, String> response = new LinkedHashMap<>();
        response.put("answer", answer == null ? "暂时没有生成回答，请重试。" : answer);
        return ResponseEntity.ok(response);
    }

    public static class ChatInput {
        @NotBlank
        @Size(max = 2000)
        public String message;
    }
}
