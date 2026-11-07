package co.tz.kikomo.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="kikomo")
public record AppConfig(String paypalMode, String paypalBaseUrl, String publicUrl, String demoMode, long hardMaxTxMinor, String fetchAllowedHosts) {
  public AppConfig { if(paypalMode==null) paypalMode="mock"; if(paypalBaseUrl==null) paypalBaseUrl="https://api-m.sandbox.paypal.com"; if(publicUrl==null) publicUrl="http://localhost:8080"; if(hardMaxTxMinor==0) hardMaxTxMinor=100000; }
}
