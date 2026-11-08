package co.tz.kikomo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminSessionTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final AdminSession session = new AdminSession(SECRET, "https://kikomo.example");

    @Test
    void issuedTokenVerifies() {
        assertTrue(session.verify(session.issue()));
    }

    @Test
    void legacyAndForgedValuesRejected() {
        assertFalse(session.verify("ok-admin"));
        assertFalse(session.verify("ok-anything"));
        assertFalse(session.verify(""));
        assertFalse(session.verify(null));
        assertFalse(session.verify("9999999999.abc.def"));
    }

    @Test
    void tamperedTokenRejected() {
        String t = session.issue();
        String extended = "9" + t; // push expiry far into the future
        assertFalse(session.verify(extended));
    }

    @Test
    void tokenFromOtherSecretRejected() {
        AdminSession other = new AdminSession("ffffffffffffffffffffffffffffffff", "https://kikomo.example");
        assertFalse(session.verify(other.issue()));
    }

    @Test
    void expiredTokenRejected() {
        String t = session.issue();
        String sig = t.substring(t.lastIndexOf('.') + 1);
        String nonce = t.substring(t.indexOf('.') + 1, t.lastIndexOf('.'));
        assertFalse(session.verify("1." + nonce + "." + sig));
    }

    @Test
    void cookieAttributes() {
        String c = session.loginCookie().toString();
        assertTrue(c.startsWith(AdminSession.COOKIE_NAME + "="));
        assertTrue(c.contains("HttpOnly"));
        assertTrue(c.contains("Secure"));
        assertTrue(c.contains("SameSite=Strict"));
        assertFalse(new AdminSession(SECRET, "http://localhost:8080").loginCookie().toString().contains("Secure"));
    }

    @Test
    void passwordMatches() {
        assertTrue(AdminSession.passwordMatches("pw", "pw"));
        assertFalse(AdminSession.passwordMatches("pw", "px"));
        assertFalse(AdminSession.passwordMatches("pw", null));
    }
}
