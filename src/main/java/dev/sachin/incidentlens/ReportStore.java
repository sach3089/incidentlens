package dev.sachin.incidentlens;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static dev.sachin.incidentlens.Models.*;
@Repository
class ReportStore {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    ReportStore(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    void save(Report r){jdbc.update("INSERT INTO investigations(id, service, payload) VALUES(?,?,?)",r.id(),r.service(),json(r));}
    Optional<Report> find(String id){return jdbc.query("SELECT payload FROM investigations WHERE id=?",(rs,n)->read(rs.getString(1),Report.class),id).stream().findFirst();}
    Deployment save(DeploymentInput d){var event=new Deployment(UUID.randomUUID().toString(),d.service(),d.version(),d.occurredAt(),d.description());jdbc.update("INSERT INTO deployment_events(id,service,occurred_at,payload) VALUES(?,?,?,?)",event.id(),event.service(),Timestamp.from(event.occurredAt()),json(event));return event;}
    List<Deployment> deployments(String service,Instant from,Instant to){return jdbc.query("SELECT payload FROM deployment_events WHERE service=? AND occurred_at>=? AND occurred_at<=? ORDER BY occurred_at LIMIT 50",(rs,n)->read(rs.getString(1),Deployment.class),service,Timestamp.from(from),Timestamp.from(to));}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException("Could not serialize record",e);}}
    private <T>T read(String s,Class<T> type){try{return mapper.readValue(s,type);}catch(Exception e){throw new IllegalStateException("Could not read record",e);}}
}
