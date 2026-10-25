package dev.sachin.incidentlens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"app.mode=incident","spring.security.user.password=test-only"})
@AutoConfigureMockMvc
class ApiTest {
 @Autowired MockMvc mvc;
 @Test void protectsInvestigationData()throws Exception{mvc.perform(get("/api/v1/investigations/missing")).andExpect(status().isUnauthorized());}
 @Test void validatesServiceInsteadOfAllowingQueryInjection()throws Exception{mvc.perform(post("/api/v1/investigations").with(httpBasic("analyst","test-only")).contentType("application/json").content("{\"service\":\"injected-service\",\"environment\":\"local\",\"from\":\"2026-01-01T00:00:00Z\",\"to\":\"2026-01-01T00:05:00Z\"}")).andExpect(status().isBadRequest());}
 @Test void persistsDeploymentEvent()throws Exception{mvc.perform(post("/api/v1/deployment-events").with(httpBasic("analyst","test-only")).contentType("application/json").content("{\"service\":\"order-service\",\"version\":\"demo-v2\",\"occurredAt\":\"2026-01-01T00:00:00Z\",\"description\":\"Synthetic deployment\"}")).andExpect(status().isCreated()).andExpect(jsonPath("version").value("demo-v2"));}
}
