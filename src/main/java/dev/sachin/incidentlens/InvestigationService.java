package dev.sachin.incidentlens;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static dev.sachin.incidentlens.Models.*;
@Service
class InvestigationService {
    private final EvidenceClient client;private final ReportStore store;private final String grafana;
    private final Semaphore capacity=new Semaphore(2);
    InvestigationService(EvidenceClient client,ReportStore store,@Value("${app.grafana-url}")String grafana){this.client=client;this.store=store;this.grafana=grafana;}
    Report investigate(InvestigationRequest request) {
        long seconds=Duration.between(request.from(),request.to()).getSeconds();
        if(seconds<60||seconds>1800||request.to().isAfter(Instant.now().plusSeconds(15)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Window must be 60..1800 seconds and not in the future");
        if(!capacity.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Two investigations already running; retry later");
        try {
            var sources=List.of(client.metrics(request),client.logs(request),client.traces(request));
            var events=store.deployments(request.service(),request.from().minusSeconds(300),request.to());
            var findings=derive(sources,events);
            var report=new Report(UUID.randomUUID().toString(),request.service(),request.environment(),request.from(),request.to(),Instant.now(),sources.stream().anyMatch(s->s.status().equals("UNAVAILABLE"))?"PARTIAL":"COMPLETE",sources,events,findings,grafana+"/d/incidentlens/services?from="+request.from().toEpochMilli()+"&to="+request.to().toEpochMilli(),List.of("Evidence is bounded and may be incomplete; COMPLETE means queries completed, not proof of a complete incident record.","Temporal association does not establish causation. No automated remediation or AI root-cause claim.","Deployment events are user-supplied and must not contain confidential data."));
            store.save(report);return report;
        } finally {capacity.release();}
    }
    static List<Finding> derive(List<SourceEvidence> sources,List<Deployment> deployments){
        List<Finding> findings=new ArrayList<>();
        for(var e:sources){
            if(e.status().equals("UNAVAILABLE"))findings.add(new Finding("COVERAGE_GAP",e.source()+" was unavailable; its absence is not evidence of health.",List.of(e.source())));
            if(e.status().equals("EMPTY"))findings.add(new Finding("COVERAGE_GAP","No matching samples returned by "+e.source()+"; check time range, collection and traffic.",List.of(e.source())));
            if(e.source().equals("prometheus") && e.data() instanceof Map<?,?> m && m.get("errorRate") instanceof Number n && n.doubleValue()>0){
                findings.add(new Finding("OBSERVATION","Estimated 5xx fraction in window: "+String.format(Locale.ROOT,"%.2f%%",n.doubleValue()*100),List.of("prometheus")));
                if(m.get("baselineErrorRate") instanceof Number b && n.doubleValue()>b.doubleValue())findings.add(new Finding("OBSERVATION","Estimated 5xx fraction is higher than the preceding equal-length baseline.",List.of("prometheus")));
            }
        }
        if(!deployments.isEmpty())findings.add(new Finding("HYPOTHESIS","A deployment event is near this window. Compare its change with the logs and traces; timing alone does not prove causation.",List.of("deployment-events")));
        return List.copyOf(findings);
    }
}
