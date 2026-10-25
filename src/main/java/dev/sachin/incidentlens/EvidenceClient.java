package dev.sachin.incidentlens;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static dev.sachin.incidentlens.Models.*;
@Component
class EvidenceClient {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper;
    private final String prometheus,loki,tempo;
    EvidenceClient(ObjectMapper mapper,@Value("${app.prometheus-url}") String prometheus,
       @Value("${app.loki-url}") String loki,@Value("${app.tempo-url}") String tempo) {
        this.mapper=mapper;this.prometheus=prometheus;this.loki=loki;this.tempo=tempo;
    }
    private JsonNode get(String base,String path,Map<String,String> params) throws Exception {
        StringJoiner q=new StringJoiner("&");
        params.forEach((k,v)->q.add(URLEncoder.encode(k,java.nio.charset.StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,java.nio.charset.StandardCharsets.UTF_8)));
        HttpResponse<java.io.InputStream> r=http.send(HttpRequest.newBuilder(URI.create(base+path+"?"+q)).timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var in=r.body()) {
            if(r.statusCode()!=200) throw new IllegalStateException("backend status "+r.statusCode());
            byte[] bytes=in.readNBytes(1_000_001); if(bytes.length>1_000_000) throw new IllegalStateException("backend response too large");
            return mapper.readTree(bytes);
        }
    }
    private Double number(String query,Instant at) throws Exception {
        JsonNode root=get(prometheus,"/api/v1/query",Map.of("query",query,"time",Long.toString(at.getEpochSecond())));
        if(!root.path("status").asText().equals("success")) throw new IllegalStateException("query unsuccessful");
        JsonNode result=root.path("data").path("result");
        if(result.isEmpty()) return null;
        double value=Double.parseDouble(result.get(0).path("value").get(1).asText());
        return Double.isFinite(value)?value:null;
    }
    SourceEvidence metrics(InvestigationRequest r) {
        long seconds=Duration.between(r.from(),r.to()).getSeconds();
        String labels="{service=\""+r.service()+"\",uri!~\"/actuator.*\"}";
        String all="sum(increase(http_server_requests_seconds_count"+labels+"["+seconds+"s]))";
        String errors="sum(increase(http_server_requests_seconds_count{service=\""+r.service()+"\",status=~\"5..\",uri!~\"/actuator.*\"}["+seconds+"s]))";
        String p95="histogram_quantile(0.95,sum by(le)(rate(http_server_requests_seconds_bucket"+labels+"["+seconds+"s])))";
        String queries=all+"\n"+errors+"\n"+p95;
        try {
            Double requests=number(all,r.to()), failures=number(errors,r.to()), baselineRequests=number(all,r.from()), baselineFailures=number(errors,r.from()),latency=number(p95,r.to());
            Map<String,Object> data=new LinkedHashMap<>();
            data.put("requests",requests);data.put("serverErrors",failures);data.put("p95Seconds",latency);
            data.put("errorRate",requests!=null&&requests>0?(failures==null?0:failures)/requests:null);
            data.put("baselineErrorRate",baselineRequests!=null&&baselineRequests>0?(baselineFailures==null?0:baselineFailures)/baselineRequests:null);
            return new SourceEvidence("prometheus",requests==null?"EMPTY":"OK",data,"Counters are sampled and extrapolated; counts are estimates. Baseline is the equally sized preceding window. No samples is not healthy traffic.",queries);
        } catch(Exception ex) { return unavailable("prometheus",queries); }
    }
    SourceEvidence logs(InvestigationRequest r) {
        String query="{service=\""+r.service()+"\"} | json | level=\"ERROR\"";
        try {
            JsonNode root=get(loki,"/loki/api/v1/query_range",Map.of("query",query,"start",r.from().getEpochSecond()+"000000000","end",r.to().getEpochSecond()+"000000000","limit","30","direction","backward"));
            if(!root.path("status").asText().equals("success")) throw new IllegalStateException();
            List<Map<String,String>> logs=new ArrayList<>();
            for(JsonNode stream:root.path("data").path("result")) for(JsonNode v:stream.path("values")) {
                if(logs.size()==30) break;
                JsonNode line=mapper.readTree(v.get(1).asText());
                // Allowlist only the fields used in this synthetic demo, never entire raw logs.
                logs.add(Map.of("timestampNanos",v.get(0).asText(),"message",safe(line.path("message").asText()),"traceId",line.path("trace_id").asText("")));
            }
            return new SourceEvidence("loki",logs.isEmpty()?"EMPTY":"OK",logs,"At most 30 recent ERROR lines, selected fields only; not a complete incident record.",query);
        } catch(Exception ex) { return unavailable("loki",query); }
    }
    SourceEvidence traces(InvestigationRequest r) {
        String query="{ resource.service.name = \""+r.service()+"\" && status = error }";
        try {
            JsonNode root=get(tempo,"/api/search",Map.of("q",query,"start",Long.toString(r.from().getEpochSecond()),"end",Long.toString(r.to().getEpochSecond()),"limit","10"));
            List<Map<String,String>> traces=new ArrayList<>();
            for(JsonNode t:root.path("traces")) {
                if(traces.size()==10)break;
                traces.add(Map.of("traceId",t.path("traceID").asText(),"rootService",t.path("rootServiceName").asText(),"durationMs",t.path("durationMs").asText()));
            }
            return new SourceEvidence("tempo",traces.isEmpty()?"EMPTY":"OK",traces,"At most 10 traces. A matching error span can belong to any operation in the trace; inspect it before attributing cause. Retention, export delays and sampling can leave gaps.",query);
        } catch(Exception ex) { return unavailable("tempo",query); }
    }
    private SourceEvidence unavailable(String source,String query){return new SourceEvidence(source,"UNAVAILABLE",Map.of(),"Query failed or exceeded configured bounds. No conclusion is drawn from this missing source.",query);}
    private String safe(String s){return s.substring(0,Math.min(s.length(),400)).replaceAll("(?i)(password|token|secret)=[^\\s]+","$1=[REDACTED]").replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[EMAIL]");}
}
