package io.github.ghosthack.turismo;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

public class TurismoTest {

    @After
    public void tearDown() {
        Turismo.reset();
    }

    // ---------------------------------------------------------------
    // Route resolution
    // ---------------------------------------------------------------

    @Test
    public void testExactRouteResolution() {
        Runnable action = () -> {};
        Turismo.get("/hello", action);

        Turismo.RouteMatch match = Turismo.resolve("GET", "/hello");
        assertSame(action, match.action);
        assertTrue(match.params.isEmpty());
    }

    @Test
    public void testExactRouteMethodIsolation() {
        Runnable getAction = () -> {};
        Runnable postAction = () -> {};
        Turismo.get("/resource", getAction);
        Turismo.post("/resource", postAction);

        assertSame(getAction, Turismo.resolve("GET", "/resource").action);
        assertSame(postAction, Turismo.resolve("POST", "/resource").action);
    }

    @Test
    public void testNoMatchReturnsNotFoundHandler() {
        Turismo.RouteMatch match = Turismo.resolve("GET", "/missing");
        assertNotNull(match.action);
        assertTrue(match.params.isEmpty());
    }

    @Test
    public void testPatternRouteWithParam() {
        Runnable action = () -> {};
        Turismo.get("/users/:id", action);

        Turismo.RouteMatch match = Turismo.resolve("GET", "/users/42");
        assertSame(action, match.action);
        assertEquals("42", match.params.get("id"));
    }

    @Test
    public void testPatternRouteWithMultipleParams() {
        Runnable action = () -> {};
        Turismo.get("/users/:userId/posts/:postId", action);

        Turismo.RouteMatch match = Turismo.resolve("GET", "/users/7/posts/99");
        assertSame(action, match.action);
        assertEquals("7", match.params.get("userId"));
        assertEquals("99", match.params.get("postId"));
    }

    @Test
    public void testWildcardRoute() {
        Runnable action = () -> {};
        Turismo.get("/files/*/download", action);

        Turismo.RouteMatch match = Turismo.resolve("GET", "/files/report/download");
        assertSame(action, match.action);
        assertTrue(match.params.isEmpty());
    }

    @Test
    public void testPatternRouteMethodMismatch() {
        Turismo.get("/users/:id", () -> {});

        Turismo.RouteMatch match = Turismo.resolve("POST", "/users/42");
        // Should fall through to not-found
        assertEquals(0, match.params.size());
    }

    @Test
    public void testPatternRouteSegmentCountMismatch() {
        Turismo.get("/users/:id", () -> {});

        // Too many segments
        Turismo.RouteMatch match = Turismo.resolve("GET", "/users/42/extra");
        assertTrue(match.params.isEmpty());
    }

    @Test
    public void testExactRoutePriorityOverPattern() {
        Runnable exact = () -> {};
        Runnable pattern = () -> {};
        Turismo.get("/users/admin", exact);
        Turismo.get("/users/:id", pattern);

        // Exact match should win
        assertSame(exact, Turismo.resolve("GET", "/users/admin").action);
    }

    @Test
    public void testCustomNotFoundHandler() {
        Runnable custom = () -> {};
        Turismo.notFound(custom);

        assertSame(custom, Turismo.resolve("GET", "/whatever").action);
    }

    @Test
    public void testAllHttpMethods() {
        Runnable a = () -> {};
        Turismo.get("/a", a);
        Turismo.post("/a", a);
        Turismo.put("/a", a);
        Turismo.delete("/a", a);
        Turismo.patch("/a", a);
        Turismo.head("/a", a);
        Turismo.options("/a", a);

        assertSame(a, Turismo.resolve("GET", "/a").action);
        assertSame(a, Turismo.resolve("POST", "/a").action);
        assertSame(a, Turismo.resolve("PUT", "/a").action);
        assertSame(a, Turismo.resolve("DELETE", "/a").action);
        assertSame(a, Turismo.resolve("PATCH", "/a").action);
        assertSame(a, Turismo.resolve("HEAD", "/a").action);
        assertSame(a, Turismo.resolve("OPTIONS", "/a").action);
    }

    @Test
    public void testResetClearsRoutes() {
        Turismo.get("/hello", () -> {});
        Turismo.reset();

        // Should be not-found now
        Turismo.RouteMatch match = Turismo.resolve("GET", "/hello");
        assertTrue(match.params.isEmpty());
    }

    // ---------------------------------------------------------------
    // Static API with mock context
    // ---------------------------------------------------------------

    @Test
    public void testStaticApiDelegatesToContext() {
        MockContext ctx = new MockContext("GET", "/test");
        Turismo.get("/test", () -> {
            assertEquals("GET", Turismo.method());
            assertEquals("/test", Turismo.path());
            Turismo.status(201);
            Turismo.header("X-Custom", "value");
            Turismo.type("text/plain");
            Turismo.print("hello");
        });

        Turismo.handle(ctx);

        assertEquals(201, ctx.statusCode);
        assertEquals("value", ctx.responseHeaders.get("X-Custom"));
        assertEquals("text/plain", ctx.responseHeaders.get("Content-Type"));
        assertEquals("hello", ctx.printed.toString());
    }

    @Test
    public void testParamAccessInAction() {
        MockContext ctx = new MockContext("GET", "/users/42");
        Turismo.get("/users/:id", () -> {
            Turismo.print("id=" + Turismo.param("id"));
        });

        Turismo.handle(ctx);
        assertEquals("id=42", ctx.printed.toString());
    }

    @Test
    public void testParamFallsBackToQuery() {
        MockContext ctx = new MockContext("GET", "/search");
        ctx.queryParams.put("q", "turismo");
        Turismo.get("/search", () -> {
            Turismo.print("q=" + Turismo.param("q"));
        });

        Turismo.handle(ctx);
        assertEquals("q=turismo", ctx.printed.toString());
    }

    @Test
    public void testPathParamTakesPrecedenceOverQuery() {
        MockContext ctx = new MockContext("GET", "/items/fromPath");
        ctx.queryParams.put("id", "fromQuery");
        Turismo.get("/items/:id", () -> {
            Turismo.print(Turismo.param("id"));
        });

        Turismo.handle(ctx);
        assertEquals("fromPath", ctx.printed.toString());
    }

    @Test
    public void testParamsReturnsUnmodifiableMap() {
        MockContext ctx = new MockContext("GET", "/users/1");
        Turismo.get("/users/:id", () -> {
            Map<String, String> params = Turismo.params();
            assertEquals("1", params.get("id"));
            try {
                params.put("hack", "value");
                fail("Expected UnsupportedOperationException");
            } catch (UnsupportedOperationException expected) {
                // good
            }
        });

        Turismo.handle(ctx);
    }

    @Test(expected = IllegalStateException.class)
    public void testContextThrowsOutsideRequest() {
        Turismo.context();
    }

    // ---------------------------------------------------------------
    // Redirect validation
    // ---------------------------------------------------------------

    @Test
    public void testRedirectValidLocation() {
        MockContext ctx = new MockContext("GET", "/old");
        Turismo.get("/old", () -> Turismo.redirect("/new"));

        Turismo.handle(ctx);
        assertEquals(302, ctx.statusCode);
        assertEquals("/new", ctx.responseHeaders.get("Location"));
    }

    @Test
    public void testMovedPermanently() {
        MockContext ctx = new MockContext("GET", "/old");
        Turismo.get("/old", () -> Turismo.movedPermanently("/new"));

        Turismo.handle(ctx);
        assertEquals(301, ctx.statusCode);
        assertEquals("/new", ctx.responseHeaders.get("Location"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRedirectRejectsCR() {
        Turismo.validateLocation("/bad\rlocation");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRedirectRejectsLF() {
        Turismo.validateLocation("/bad\nlocation");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRedirectRejectsNull() {
        Turismo.validateLocation(null);
    }

    // ---------------------------------------------------------------
    // Mock context for unit tests
    // ---------------------------------------------------------------

    static class MockContext implements Context {
        final String method;
        final String path;
        final Map<String, String> queryParams = new HashMap<>();
        final Map<String, String> requestHeaders = new HashMap<>();
        final Map<String, String> responseHeaders = new HashMap<>();
        final StringBuilder printed = new StringBuilder();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int statusCode = 200;

        MockContext(String method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override public String method() { return method; }
        @Override public String path() { return path; }
        @Override public String query(String name) { return queryParams.get(name); }
        @Override public String header(String name) { return requestHeaders.get(name); }
        @Override public InputStream body() { return new ByteArrayInputStream(new byte[0]); }
        @Override public void status(int code) { this.statusCode = code; }
        @Override public void header(String name, String value) { responseHeaders.put(name, value); }
        @Override public void print(String text) { printed.append(text); }
        @Override public OutputStream output() { return outputStream; }
    }
}
