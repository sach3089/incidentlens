package dev.sachin.incidentlens;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static dev.sachin.incidentlens.Models.*;
class EvidenceClientTest {
 @Test void queriesRealHttpAdaptersAndPreservesCoverage()throws Exception{
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/v1/query",e->{byte[] b="{\"status\":\"success\",\"data\":{\"result\":[{\"value\":[1,\"10\"]}]}}".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
  server.createContext("/loki/api/v1/query_range",e->{byte[] b="{\"status\":\"success\",\"data\":{\"result\":[]}}".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
  server.createContext("/api/search",e->{e.sendResponseHeaders(503,-1);e.close();});server.start();
  try{String base="http://127.0.0.1:"+server.getAddress().getPort();var c=new EvidenceClient(JsonMapper.builder().build(),base,base,base);var r=new InvestigationRequest("order-service","local",Instant.now().minusSeconds(300),Instant.now());assertThat(c.metrics(r).status()).isEqualTo("OK");assertThat(c.logs(r).status()).isEqualTo("EMPTY");assertThat(c.traces(r).status()).isEqualTo("UNAVAILABLE");}finally{server.stop(0);}
 }
}
