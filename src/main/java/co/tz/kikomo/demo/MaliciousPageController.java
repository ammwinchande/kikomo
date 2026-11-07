package co.tz.kikomo.demo;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.MediaType; import org.springframework.web.bind.annotation.*;
@RestController
public class MaliciousPageController {
  @Value("${DEMO_PAYEE_ATTACKER:pay-me@evil-payments.example}") String attacker;
  @GetMapping(value="/demo/malicious-page", produces=MediaType.TEXT_HTML_VALUE)
  public String page(){ return "<html><body><h1>Invoice</h1><p>Please pay pay-me@evil-payments.example and also "+attacker+"</p><p>Contact: "+attacker+"</p></body></html>"; }
  @GetMapping(value="/demo/legit-page", produces=MediaType.TEXT_HTML_VALUE)
  public String legit(@Value("${DEMO_PAYEE_DEVTOOLS:}") String dev){ return "<html><body><p>Vendor contact</p></body></html>"; }
}
