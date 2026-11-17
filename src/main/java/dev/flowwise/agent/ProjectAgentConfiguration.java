package dev.flowwise.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
public class ProjectAgentConfiguration {
    @Bean
    ProjectAgentService projectAgentService(ChatClient.Builder builder, ProjectAgentTools tools) {
        return new ProjectAgentService(builder, tools);
    }
}
