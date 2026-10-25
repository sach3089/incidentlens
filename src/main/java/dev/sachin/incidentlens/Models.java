package dev.sachin.incidentlens;
import java.time.Instant;
import java.util.*;
import jakarta.validation.constraints.*;
public final class Models {
    private Models() {}
    public record InvestigationRequest(@NotBlank @Pattern(regexp="order-service|inventory-service") String service,
        @NotBlank @Pattern(regexp="local") String environment, @NotNull Instant from, @NotNull Instant to) {}
    public record DeploymentInput(@NotBlank @Pattern(regexp="order-service|inventory-service") String service,
        @NotBlank @Size(max=80) String version, @NotNull Instant occurredAt,
        @NotBlank @Size(max=200) String description) {}
    public record Deployment(String id, String service, String version, Instant occurredAt, String description) {}
    public record SourceEvidence(String source, String status, Object data, String limitation, String query) {}
    public record Finding(String type, String statement, List<String> evidenceSources) {}
    public record Report(String id, String service, String environment, Instant from, Instant to,
        Instant createdAt, String status, List<SourceEvidence> evidence, List<Deployment> deployments,
        List<Finding> findings, String grafanaUrl, List<String> limitations) {}
}
