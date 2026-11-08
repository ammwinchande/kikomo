package co.tz.kikomo.gateway;

import co.tz.kikomo.config.AppConfig;
import co.tz.kikomo.db.UntrustedRepository;
import co.tz.kikomo.engine.Normalize;
import co.tz.kikomo.shared.error.ToolExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * kikomo_fetch_url (SPEC section 11): fetches text from an allow-listed host, extracts e-mail addresses and
 * records them as untrusted for the calling agent, so a payee injected by fetched content is denied later.
 */
@Component
public class UrlFetcher {
    static final int MAX_BODY_BYTES = 1_048_576;
    private static final Logger log = LoggerFactory.getLogger(UrlFetcher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Set<String> SCHEMES = Set.of("http", "https");
    /**
     * Characters that end an e-mail candidate when scanning outwards from an '@'.
     */
    private static final String SEPARATORS = "<>\"'(),;:@[]";
    /**
     * Upper bound for one side of a candidate (RFC 5321 limits are 64/255).
     */
    private static final int MAX_PART_LENGTH = 255;

    private final HttpClient http;
    private final UntrustedRepository untrusted;
    private final Set<String> allowedHosts;

    public UrlFetcher(HttpClient outboundHttpClient, UntrustedRepository untrusted, AppConfig config) {
        this.http = outboundHttpClient;
        this.untrusted = untrusted;
        this.allowedHosts = Set.copyOf(config.effectiveFetchHosts());
        log.info("kikomo_fetch_url allowed hosts: {}", allowedHosts);
    }

    /**
     * Finds e-mail addresses in O(n): for every '@' it expands left and right over non-separator characters
     * (bounded), then validates the candidate with {@link Normalize#isValidEmail}. A regex scan would retry from
     * every start position on long runs without an '@' (quadratic time on hostile pages).
     */
    static List<String> extractEmails(String text) {
        Set<String> found = new LinkedHashSet<>();
        for (int at = text.indexOf('@'); at >= 0; at = text.indexOf('@', at + 1)) {
            int start = at;
            while (start > 0 && at - start < MAX_PART_LENGTH && isCandidateChar(text.charAt(start - 1))) start--;
            int end = at + 1;
            while (end < text.length() && end - at <= MAX_PART_LENGTH && isCandidateChar(text.charAt(end))) end++;
            if (start < at && end > at + 1) {
                String candidate = Normalize.payee(stripTrailingDots(text.substring(start, end)));
                if (Normalize.isValidEmail(candidate)) found.add(candidate);
            }
        }
        return new ArrayList<>(found);
    }

    private static boolean isCandidateChar(char c) {
        return !Character.isWhitespace(c) && SEPARATORS.indexOf(c) < 0;
    }

    private static URI parse(String url) {
        if (url == null) return null;
        try {
            return URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String stripTrailingDots(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    /**
     * True only for http(s) URLs whose host is in FETCH_ALLOWED_HOSTS (or the PUBLIC_URL host).
     */
    public boolean isAllowed(String url) {
        URI uri = parse(url);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) return false;
        return SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
                && allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
    }

    /**
     * Fetches {@code url} (already checked with {@link #isAllowed}) and records the e-mails found in it.
     */
    public Map<String, Object> fetchAndRecord(UUID agentId, String url) {
        if (!isAllowed(url)) {
            throw new ToolExecutionException("URL is not allowed");
        }
        String body = fetch(url);
        List<String> emails = extractEmails(body);
        emails.forEach(email -> untrusted.add(agentId, email, url));
        log.info("Fetched {} for agent {}: {} untrusted e-mail(s) recorded", url, agentId, emails.size());
        return Map.of(ToolEnvelope.RESULT_FETCHED_URL, url, ToolEnvelope.RESULT_EMAILS_FOUND, emails);
    }

    private String fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new ToolExecutionException("Fetch returned HTTP " + response.statusCode());
                }
                return new String(in.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("Fetch was interrupted", e);
        } catch (IOException e) {
            throw new ToolExecutionException("Fetch failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
