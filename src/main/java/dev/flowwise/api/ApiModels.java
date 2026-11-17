package dev.flowwise.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class ApiModels {
    private ApiModels() { }

    public static class ProjectInput {
        @NotBlank @Size(max = 100)
        public String name;
        @Size(max = 500)
        public String description;
    }

    public static class TaskInput {
        @NotBlank @Size(max = 160)
        public String title;
        @Size(max = 80)
        public String assignee;
        public String priority;
        public LocalDate dueDate;
    }

    public static class TaskDeliveryInput {
        @Size(max = 2000)
        public String note;
        @Size(max = 500)
        public String url;
    }

    public static class ProjectUpdateInput extends ProjectInput { }

    public static class DecisionInput {
        public boolean approved;
        @Size(max = 500)
        public String comment;
    }

    public static class RegistrationInput {
        @NotBlank @Size(min = 3, max = 32)
        public String username;
        @NotBlank @Size(min = 8, max = 72)
        public String password;
        @Size(max = 16)
        public String role;
    }
}
