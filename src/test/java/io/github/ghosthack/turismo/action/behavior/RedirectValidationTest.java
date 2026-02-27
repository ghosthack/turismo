package io.github.ghosthack.turismo.action.behavior;

import org.junit.Test;

public class RedirectValidationTest {

    @Test
    public void testValidLocation() {
        Redirect.validateLocation("/path/to/page");
        Redirect.validateLocation("https://example.com/page");
        Redirect.validateLocation("/path?query=value&other=123");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullLocation() {
        Redirect.validateLocation(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLocationWithCR() {
        Redirect.validateLocation("/path\rX-Injected: true");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLocationWithLF() {
        Redirect.validateLocation("/path\nX-Injected: true");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLocationWithCRLF() {
        Redirect.validateLocation("/path\r\nX-Injected: true");
    }
}
