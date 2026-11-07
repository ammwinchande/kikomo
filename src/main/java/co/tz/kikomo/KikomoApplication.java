package co.tz.kikomo;
import co.tz.kikomo.config.AppConfig; import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.boot.context.properties.EnableConfigurationProperties;
@SpringBootApplication @EnableConfigurationProperties(AppConfig.class)
public class KikomoApplication { public static void main(String[] args){ SpringApplication.run(KikomoApplication.class, args); } }
