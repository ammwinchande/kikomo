package co.tz.kikomo.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class UrlFetcherTest {
    @Test
    void extractsNormalizedDistinctEmails() {
        String html = "<p>Pay PAY-ME@Evil-Payments.Example.</p><a href=\"mailto:x@y.io\">x@y.io</a> not@valid";
        assertEquals(List.of("pay-me@evil-payments.example", "x@y.io"), UrlFetcher.extractEmails(html));
    }

    @Test
    void extractionIsLinearOnHostileInput() {
        String noAt = "a".repeat(1_000_000);
        String manyAts = "a@".repeat(500_000);
        String longParts = "a".repeat(200_000) + "@" + "b.".repeat(100_000);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), () -> {
            UrlFetcher.extractEmails(noAt);
            UrlFetcher.extractEmails(manyAts);
            UrlFetcher.extractEmails(longParts);
        });
    }
}
