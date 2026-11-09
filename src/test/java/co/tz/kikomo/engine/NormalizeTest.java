package co.tz.kikomo.engine;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class NormalizeTest {

    @Test
    void trimsLowercases() {
        assertEquals("dev@example.com", Normalize.payee("  DEV@Example.COM "));
    }

    @Test
    void nfkcFullwidth() {
        assertEquals("dev@example.com", Normalize.payee("ｄｅｖ@example.com"));
    }

    @Test
    void validEmail() {
        assertTrue(Normalize.isValidEmail("a.b+1@x.co"));
    }

    @Test
    void invalidEmail() {
        assertFalse(Normalize.isValidEmail("not-email"));
        assertFalse(Normalize.isValidEmail("a@b"));
    }

    @Test
    void nullPayee() {
        assertEquals("", Normalize.payee(null));
    }

    @Test
    void moneyParse() {
        assertEquals(1000, co.tz.kikomo.shared.Money.parseMinor("10.00"));
        assertEquals(5, co.tz.kikomo.shared.Money.parseMinor("0.05"));
        assertEquals(100, co.tz.kikomo.shared.Money.parseMinor("1"));
    }

    @Test
    void moneyInvalid() {
        assertThrows(IllegalArgumentException.class, () -> co.tz.kikomo.shared.Money.parseMinor("1.999"));
    }

    @Test
    void lowercasingIgnoresDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals("title@x.io", Normalize.payee("TITLE@X.IO"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void moneyRejectsOverflowAndTooManyDigits() {
        assertFalse(co.tz.kikomo.shared.Money.isValidFormat("100000000000000000"));
        assertTrue(co.tz.kikomo.shared.Money.isValidFormat("9999999999999.99"));
        assertThrows(IllegalArgumentException.class, () -> co.tz.kikomo.shared.Money.parseMinor("100000000000000000"));
    }

    @Test
    void moneyFormatIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("12.50", co.tz.kikomo.shared.Money.formatMinor(1250));
            assertEquals("0.05", co.tz.kikomo.shared.Money.formatMinor(5));
        } finally {
            Locale.setDefault(original);
        }
    }
}
