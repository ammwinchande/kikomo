package co.tz.kikomo;

import co.tz.kikomo.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.net.URI;

@SpringBootApplication
@EnableConfigurationProperties(AppConfig.class)
public class KikomoApplication {
    private static final Logger log = LoggerFactory.getLogger(KikomoApplication.class);

    public static void main(String[] args) {
        applyNeonEnv();
        SpringApplication.run(KikomoApplication.class, args);
    }

    // Neon (`neon deploy`) provides DATABASE_URL / DATABASE_URL_UNPOOLED as
    // postgres://user:pass@host:port/db?sslmode=require. Spring JDBC needs a
    // jdbc:postgresql:// URL plus separate credentials, so translate here.
    // UNPOOLED (direct) is preferred: Flyway + pg_advisory_xact_lock are safest off PgBouncer.
    static void applyNeonEnv() {
        if (System.getProperty("spring.datasource.url") != null) {
            return;
        }

        String explicit = env("SPRING_DATASOURCE_URL");
        if (explicit != null && !explicit.isBlank()) {
            return;
        }

        String raw = env("DATABASE_URL_UNPOOLED");

        if (raw == null || raw.isBlank()) {
            raw = env("DATABASE_URL");
        }

        if (raw == null || raw.isBlank()) {
            return;
        }

        try {
            URI uri = URI.create(raw);
            String userInfo = uri.getUserInfo();
            if (userInfo == null) {
                return;
            }
            String user = userInfo;
            String pass = "";
            int colon = userInfo.indexOf(':');
            if (colon >= 0) {
                user = userInfo.substring(0, colon);
                pass = userInfo.substring(colon + 1);
            }
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String query = uri.getQuery();
            if (query == null || !query.contains("sslmode")) {
                query = (query == null ? "" : query + "&") + "sslmode=require";
            }
            String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath() + "?" + query;
            System.setProperty("spring.datasource.url", jdbc);
            System.setProperty("spring.datasource.username", user);
            System.setProperty("spring.datasource.password", pass);
            log.info("Neon datasource wired from DATABASE_URL_UNPOOLED/DATABASE_URL (host={})", uri.getHost());
        } catch (IllegalArgumentException e) {
            log.error("Neon env wiring skipped: DATABASE_URL is not a valid URI", e);
        }
    }

    private static String env(String property) {
        return System.getenv(property);
    }
}
