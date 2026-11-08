package co.tz.kikomo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Stateless, HMAC-signed admin session cookie.
 * Value format: {@code <expiryEpochSeconds>.<nonce>.<base64url(HMAC-SHA256(secret, expiry.nonce))>}
 */
@Component
public class AdminSession {
    public static final String COOKIE_NAME = "KIKOMO_ADMIN";
    static final Duration TTL = Duration.ofHours(12);
    private static final Logger log = LoggerFactory.getLogger(AdminSession.class);
    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;
    private final boolean secureCookies;

    @Autowired
    public AdminSession(AppConfig config) {
        this(config.admin().sessionSecret(), config.publicUrl());
    }

    AdminSession(String secret, String publicUrl) {
        byte[] keyBytes;
        if (secret == null || secret.length() < 32) {
            log.warn("SESSION_SECRET missing or shorter than 32 chars; using a random per-process key (admin sessions reset on restart)");
            keyBytes = new byte[32];
            random.nextBytes(keyBytes);
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = new SecretKeySpec(keyBytes, HMAC);
        this.secureCookies = publicUrl != null && publicUrl.startsWith("https://");
    }

    public boolean isSecureCookies() {
        return secureCookies;
    }

    /** Constant-time password comparison. */
    public static boolean passwordMatches(String expected, String provided) {
        if (expected == null || provided == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    public String issue() {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String payload = Instant.now().plus(TTL).getEpochSecond() + "." + B64.encodeToString(nonce);
        return payload + "." + B64.encodeToString(sign(payload));
    }

    public boolean verify(String value) {
        if (value == null) return false;
        int lastDot = value.lastIndexOf('.');
        int firstDot = value.indexOf('.');
        if (firstDot <= 0 || lastDot == firstDot) return false;
        String payload = value.substring(0, lastDot);
        byte[] provided;
        long expiry;
        try {
            provided = B64D.decode(value.substring(lastDot + 1));
            expiry = Long.parseLong(value.substring(0, firstDot));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(sign(payload), provided) && Instant.now().getEpochSecond() < expiry;
    }

    public ResponseCookie loginCookie() {
        return base(issue()).maxAge(TTL).build();
    }

    public ResponseCookie logoutCookie() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .path("/")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict");
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
