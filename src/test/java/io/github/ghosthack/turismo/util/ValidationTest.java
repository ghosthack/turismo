package io.github.ghosthack.turismo.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ValidationTest {

    @Test
    public void testLocalPaths() {
        assertTrue(Validation.isLocalPath("/"));
        assertTrue(Validation.isLocalPath("/dashboard"));
        assertTrue(Validation.isLocalPath("/a/b?next=//evil.com#x"));
        assertTrue(Validation.isLocalPath("/a//b"));
    }

    @Test
    public void testNonLocalPaths() {
        for (String location : new String[] {
                "//evil.com", "/\\evil.com", "/\t/evil.com",
                "https://x", "http://evil.com/", "javascript:alert(1)",
                "evil.com", "", " /x", "\\\\evil.com", "/x\r\nSet-Cookie: a",
                "/x\u007f"}) {
            assertFalse(Validation.isLocalPath(location), location);
        }
        assertFalse(Validation.isLocalPath(null));
    }

    @Test
    public void testValidateLocationRejectsControlChars() {
        Validation.validateLocation("https://example.com/a b");
        for (char c = 0; c < 0x20; c++) {
            String location = "/a" + c + "b";
            assertThrows(IllegalArgumentException.class,
                    () -> Validation.validateLocation(location));
        }
        assertThrows(IllegalArgumentException.class,
                () -> Validation.validateLocation("/a\u007fb"));
    }
}
