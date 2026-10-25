package dev.sachin.incidentlens;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static dev.sachin.incidentlens.Models.*;
class InvestigationTest {
    @Test void separatesFactsFromHypothesesAndMissingData(){
        var sources=List.of(new SourceEvidence("prometheus","OK",Map.of("errorRate",0.2,"baselineErrorRate",0.0),"",""),new SourceEvidence("loki","UNAVAILABLE",Map.of(),"",""));
        var findings=InvestigationService.derive(sources,List.of(new Deployment("1","order-service","v2",Instant.now(),"demo")));
        assertThat(findings).anyMatch(f->f.type().equals("OBSERVATION")&&f.statement().contains("20.00%"));
        assertThat(findings).anyMatch(f->f.type().equals("HYPOTHESIS")&&f.statement().contains("does not prove"));
        assertThat(findings).anyMatch(f->f.type().equals("COVERAGE_GAP"));
    }
    @Test void rejectsUnboundedWindowsBeforeCallingBackends(){
        var client=mock(EvidenceClient.class);var store=mock(ReportStore.class);var service=new InvestigationService(client,store,"http://localhost:3000");
        assertThatThrownBy(()->service.investigate(new InvestigationRequest("order-service","local",Instant.now().minusSeconds(4000),Instant.now()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        verifyNoInteractions(client,store);
    }
}
