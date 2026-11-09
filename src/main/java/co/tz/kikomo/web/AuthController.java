package co.tz.kikomo.web;

import co.tz.kikomo.config.AdminSession;
import co.tz.kikomo.config.AppConfig;
import co.tz.kikomo.web.dto.LoginRequest;
import co.tz.kikomo.web.dto.OkResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AdminSession session;
    private final AppConfig config;

    public AuthController(AdminSession session, AppConfig config) {
        this.session = session;
        this.config = config;
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        if (AdminSession.passwordMatches(config.admin().password(), body == null ? null : body.password())) {
            log.info("Admin login from {}", request.getRemoteAddr());
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, session.loginCookie().toString()).body(OkResponse.OK_RESPONSE);
        }
        log.warn("Failed admin login from {}", request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "bad password"));
    }

    /** 200 when the admin cookie is valid; the security chain answers 401 otherwise. Lets the SPA restore a session on reload. */
    @GetMapping("/session")
    public OkResponse session() {
        return OkResponse.OK_RESPONSE;
    }

    @PostMapping("/logout")
    public ResponseEntity<OkResponse> logout() {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, session.logoutCookie().toString()).body(OkResponse.OK_RESPONSE);
    }
}
