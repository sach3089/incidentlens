package dev.sachin.incidentlens;
import java.net.URI;
import java.time.Instant;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static dev.sachin.incidentlens.Models.*;
@RestController
@ConditionalOnProperty(name="app.mode",havingValue="incident",matchIfMissing=true)
@RequestMapping("/api/v1")
class IncidentController {
    private final InvestigationService service;private final ReportStore store;
    IncidentController(InvestigationService service,ReportStore store){this.service=service;this.store=store;}
    @PostMapping("/investigations") ResponseEntity<Report> create(@Valid @RequestBody InvestigationRequest r){Report result=service.investigate(r);return ResponseEntity.created(URI.create("/api/v1/investigations/"+result.id())).body(result);}
    @GetMapping("/investigations/{id}") Report find(@PathVariable String id){return store.find(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Investigation not found"));}
    @GetMapping("/investigations/{id}/evidence") Object evidence(@PathVariable String id){return find(id).evidence();}
    @GetMapping("/investigations/{id}/timeline") Object timeline(@PathVariable String id){return find(id).deployments();}
    @PostMapping("/deployment-events") ResponseEntity<Deployment> deployment(@Valid @RequestBody DeploymentInput d){if(d.occurredAt().isAfter(Instant.now().plusSeconds(15)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Event time is in the future");return ResponseEntity.status(201).body(store.save(d));}
}
