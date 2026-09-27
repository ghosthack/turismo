package io.github.ghosthack.turismo.action.behavior;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class RedirectValidationTest {

    @Test
    public void testValidLocation() {
        Redirect.validateLocation("/path/to/page");
        Redirect.validateLocation("https://example.com/page");
        Redirect.validateLocation("/path?query=value&other=123");
    }

    @Test
    public void testNullLocation() {
        assertThrows(IllegalArgumentException.class, () -> {
            Redirect.validateLocation(null);
        });
    }

    @Test
    public void testLocationWithCR() {
        assertThrows(IllegalArgumentException.class, () -> {
            Redirect.validateLocation("/path\rX-Injected: true");
        });
    }

    @Test
    public void testLocationWithLF() {
        assertThrows(IllegalArgumentException.class, () -> {
            Redirect.validateLocation("/path\nX-Injected: true");
        });
    }

    @Test
    public void testLocationWithCRLF() {
        assertThrows(IllegalArgumentException.class, () -> {
            Redirect.validateLocation("/path\r\nX-Injected: true");
        });
    }
}
