package dev.sachin.incidentlens;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
@RestController
@ConditionalOnExpression("'${app.mode}' == 'order' or '${app.mode}' == 'inventory'")
class DemoController {
    private static final Logger log = LoggerFactory.getLogger(DemoController.class);
    record Fault(int delayMs, boolean fail) {}
    private final AtomicReference<Fault> fault = new AtomicReference<>(new Fault(0,false));
    private final String mode, key;
    private final RestClient inventory;
    DemoController(@Value("${app.mode}") String mode, @Value("${app.demo-key}") String key,
                   @Value("${app.inventory-url}") String url) {
        this.mode=mode; this.key=key;
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(1)).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(3));
        this.inventory=RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    @GetMapping("/api/v1/orders/{id}") Map<String,Object> order(@PathVariable String id) {
        if (!mode.equals("order")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        try {
            var stock=inventory.get().uri("/api/v1/inventory/widget").retrieve().body(Map.class);
            log.info("Order lookup completed");
            return Map.of("orderId", id, "inventory", stock, "observedAt", Instant.now());
        } catch (Exception ex) {
            // Fixed message: do not emit untrusted payloads or exception messages to logs.
            log.error("Inventory dependency failed or timed out");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Inventory dependency unavailable");
        }
    }
    @GetMapping("/api/v1/inventory/{sku}") Map<String,Object> inventory(@PathVariable String sku) {
        if (!mode.equals("inventory")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Fault f=fault.get();
        if(f.delayMs()>0) try { Thread.sleep(f.delayMs()); } catch(InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        if(f.fail()) { log.error("Controlled inventory failure active"); throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Controlled demo failure"); }
        log.info("Inventory lookup completed");
        return Map.of("sku",sku,"available",true);
    }
    @PostMapping("/demo/fault") Fault fault(@RequestHeader("X-Demo-Key") String supplied, @RequestBody Fault f) {
        if (!mode.equals("inventory")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(!java.security.MessageDigest.isEqual(key.getBytes(java.nio.charset.StandardCharsets.UTF_8),supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if(f.delayMs()<0 || f.delayMs()>5000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"delayMs must be 0..5000");
        fault.set(f); log.warn("Controlled fault updated: delayMs={}, fail={}",f.delayMs(),f.fail()); return f;
    }
}
