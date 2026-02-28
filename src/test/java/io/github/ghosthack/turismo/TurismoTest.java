package io.github.ghosthack.turismo;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import io.github.ghosthack.turismo.annotation.DELETE;
import io.github.ghosthack.turismo.annotation.GET;
import io.github.ghosthack.turismo.annotation.PATCH;
import io.github.ghosthack.turismo.annotation.POST;
import io.github.ghosthack.turismo.annotation.PUT;

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
        Turismo.get("/resource", getAction);
        Turismo.post("/resource", () -> {});

        assertSame(getAction, Turismo.resolve("GET", "/resource").action);
        // POST wraps the action to set status 201, so test it via handle
        assertNotNull(Turismo.resolve("POST", "/resource").action);
        assertNotSame(
                Turismo.resolve("GET", "/resource").action,
                Turismo.resolve("POST", "/resource").action);
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
        assertNotNull(Turismo.resolve("POST", "/a").action); // wrapped for 201
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
    // String body shorthand
    // ---------------------------------------------------------------

    @Test
    public void testGetStringBody() {
        MockContext ctx = new MockContext("GET", "/hello");
        Turismo.get("/hello", "Hello World!");

        Turismo.handle(ctx);
        assertEquals("Hello World!", ctx.printed.toString());
        assertEquals(200, ctx.statusCode);
    }

    @Test
    public void testPostStringBody() {
        MockContext ctx = new MockContext("POST", "/create");
        Turismo.post("/create", "Created!");

        Turismo.handle(ctx);
        assertEquals("Created!", ctx.printed.toString());
        assertEquals(201, ctx.statusCode);
    }

    @Test
    public void testPutStringBody() {
        MockContext ctx = new MockContext("PUT", "/update");
        Turismo.put("/update", "Updated");

        Turismo.handle(ctx);
        assertEquals("Updated", ctx.printed.toString());
    }

    @Test
    public void testDeleteStringBody() {
        MockContext ctx = new MockContext("DELETE", "/remove");
        Turismo.delete("/remove", "Deleted");

        Turismo.handle(ctx);
        assertEquals("Deleted", ctx.printed.toString());
    }

    @Test
    public void testPatchStringBody() {
        MockContext ctx = new MockContext("PATCH", "/patch");
        Turismo.patch("/patch", "Patched");

        Turismo.handle(ctx);
        assertEquals("Patched", ctx.printed.toString());
    }

    // ---------------------------------------------------------------
    // Default POST status 201
    // ---------------------------------------------------------------

    @Test
    public void testPostDefaultStatus201() {
        MockContext ctx = new MockContext("POST", "/items");
        Turismo.post("/items", () -> Turismo.print("created"));

        Turismo.handle(ctx);
        assertEquals(201, ctx.statusCode);
        assertEquals("created", ctx.printed.toString());
    }

    @Test
    public void testPostCanOverrideStatus() {
        MockContext ctx = new MockContext("POST", "/items");
        Turismo.post("/items", () -> { Turismo.status(200); Turismo.print("ok"); });

        Turismo.handle(ctx);
        assertEquals(200, ctx.statusCode);
    }

    // ---------------------------------------------------------------
    // Varargs print
    // ---------------------------------------------------------------

    @Test
    public void testVarargsPrint() {
        MockContext ctx = new MockContext("GET", "/greet");
        Turismo.get("/greet", () -> Turismo.print("Hello", " ", "World"));

        Turismo.handle(ctx);
        assertEquals("Hello World", ctx.printed.toString());
    }

    @Test
    public void testVarargsPrintSingleArg() {
        MockContext ctx = new MockContext("GET", "/one");
        Turismo.get("/one", () -> Turismo.print("just one"));

        Turismo.handle(ctx);
        assertEquals("just one", ctx.printed.toString());
    }

    // ---------------------------------------------------------------
    // JSON serializer
    // ---------------------------------------------------------------

    @Test
    public void testToJsonNull() {
        assertEquals("null", Turismo.toJson(null));
    }

    @Test
    public void testToJsonString() {
        assertEquals("\"hello\"", Turismo.toJson("hello"));
    }

    @Test
    public void testToJsonStringEscaping() {
        assertEquals("\"line1\\nline2\"", Turismo.toJson("line1\nline2"));
        assertEquals("\"tab\\there\"", Turismo.toJson("tab\there"));
        assertEquals("\"quote\\\"here\"", Turismo.toJson("quote\"here"));
        assertEquals("\"back\\\\slash\"", Turismo.toJson("back\\slash"));
    }

    @Test
    public void testToJsonNumbers() {
        assertEquals("42", Turismo.toJson(42));
        assertEquals("3.14", Turismo.toJson(3.14));
        assertEquals("100", Turismo.toJson(100L));
    }

    @Test
    public void testToJsonBoolean() {
        assertEquals("true", Turismo.toJson(true));
        assertEquals("false", Turismo.toJson(false));
    }

    @Test
    public void testToJsonMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "turismo");
        map.put("version", 3);
        assertEquals("{\"name\":\"turismo\",\"version\":3}", Turismo.toJson(map));
    }

    @Test
    public void testToJsonList() {
        assertEquals("[1,2,3]", Turismo.toJson(Arrays.asList(1, 2, 3)));
    }

    @Test
    public void testToJsonObjectArray() {
        assertEquals("[\"a\",\"b\"]", Turismo.toJson(new String[]{"a", "b"}));
    }

    @Test
    public void testToJsonIntArray() {
        assertEquals("[1,2,3]", Turismo.toJson(new int[]{1, 2, 3}));
    }

    @Test
    public void testToJsonNestedMap() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("ok", true);
        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("status", inner);
        assertEquals("{\"status\":{\"ok\":true}}", Turismo.toJson(outer));
    }

    @Test
    public void testToJsonEmptyMap() {
        assertEquals("{}", Turismo.toJson(Collections.emptyMap()));
    }

    @Test
    public void testToJsonEmptyList() {
        assertEquals("[]", Turismo.toJson(Collections.emptyList()));
    }

    @Test
    public void testJsonSetsContentType() {
        MockContext ctx = new MockContext("GET", "/api");
        Turismo.get("/api", () -> Turismo.json(Map.of("ok", true)));

        Turismo.handle(ctx);
        assertEquals("application/json", ctx.responseHeaders.get("Content-Type"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToJsonUnsupportedType() {
        Turismo.toJson(new Object());
    }

    @Test
    public void testToJsonLongArray() {
        assertEquals("[1,2]", Turismo.toJson(new long[]{1L, 2L}));
    }

    @Test
    public void testToJsonDoubleArray() {
        assertEquals("[1.5,2.5]", Turismo.toJson(new double[]{1.5, 2.5}));
    }

    @Test
    public void testToJsonBooleanArray() {
        assertEquals("[true,false]", Turismo.toJson(new boolean[]{true, false}));
    }

    @Test
    public void testToJsonControlCharEscaping() {
        // Control char below 0x20 that isn't \b\f\n\r\t
        assertEquals("\"\\u0001\"", Turismo.toJson("\u0001"));
    }

    // ---------------------------------------------------------------
    // PathPattern
    // ---------------------------------------------------------------

    @Test
    public void testPathPatternExactMatch() {
        PathPattern p = new PathPattern("/users/list");
        assertNotNull(p.match("/users/list".split("/")));
        assertNull(p.match("/users/other".split("/")));
    }

    @Test
    public void testPathPatternParams() {
        PathPattern p = new PathPattern("/users/:id");
        Map<String, String> result = p.match("/users/42".split("/"));
        assertNotNull(result);
        assertEquals("42", result.get("id"));
    }

    @Test
    public void testPathPatternWildcard() {
        PathPattern p = new PathPattern("/files/*/view");
        Map<String, String> result = p.match("/files/report/view".split("/"));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPathPatternNullPath() {
        new PathPattern(null);
    }

    @Test
    public void testPathPatternSegmentMismatch() {
        PathPattern p = new PathPattern("/a/b");
        assertNull(p.match("/a/b/c".split("/")));
    }

    // ---------------------------------------------------------------
    // Annotation-based controller registration
    // ---------------------------------------------------------------

    @Test
    public void testControllerGet() {
        MockContext ctx = new MockContext("GET", "/ctrl/hello");
        Turismo.controller(new TestController());

        Turismo.handle(ctx);
        assertEquals("hello", ctx.printed.toString());
    }

    @Test
    public void testControllerGetWithParam() {
        MockContext ctx = new MockContext("GET", "/ctrl/users/42");
        Turismo.controller(new TestController());

        Turismo.handle(ctx);
        assertEquals("user=42", ctx.printed.toString());
    }

    @Test
    public void testControllerPostDefaultStatus201() {
        MockContext ctx = new MockContext("POST", "/ctrl/items");
        Turismo.controller(new TestController());

        Turismo.handle(ctx);
        assertEquals(201, ctx.statusCode);
        assertEquals("created", ctx.printed.toString());
    }

    @Test
    public void testControllerPut() {
        MockContext ctx = new MockContext("PUT", "/ctrl/items");
        Turismo.controller(new TestController());

        Turismo.handle(ctx);
        assertEquals("updated", ctx.printed.toString());
    }

    @Test
    public void testControllerDelete() {
        MockContext ctx = new MockContext("DELETE", "/ctrl/items");
        Turismo.controller(new TestController());

        Turismo.handle(ctx);
        assertEquals("deleted", ctx.printed.toString());
    }

    @Test
    public void testControllerPatch() {
        MockContext ctx = new MockContext("PATCH", "/ctrl/items");
        Turismo.controller(new TestController());

        Turismo.handle(ctx);
        assertEquals("patched", ctx.printed.toString());
    }

    @Test
    public void testControllerMixedWithLambdaRoutes() {
        MockContext ctxGet = new MockContext("GET", "/lambda");
        MockContext ctxCtrl = new MockContext("GET", "/ctrl/hello");
        Turismo.get("/lambda", "from-lambda");
        Turismo.controller(new TestController());

        Turismo.handle(ctxGet);
        assertEquals("from-lambda", ctxGet.printed.toString());

        Turismo.handle(ctxCtrl);
        assertEquals("hello", ctxCtrl.printed.toString());
    }

    @Test
    public void testControllerRuntimeExceptionPropagated() {
        MockContext ctx = new MockContext("GET", "/ctrl/error");
        Turismo.controller(new TestController());

        try {
            Turismo.handle(ctx);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertEquals("test error", e.getMessage());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testControllerNoAnnotationsThrows() {
        Turismo.controller(new Object());
    }

    /** Test controller used by annotation tests. */
    static class TestController {
        @GET("/ctrl/hello")
        void hello() {
            Turismo.print("hello");
        }

        @GET("/ctrl/users/:id")
        void getUser() {
            Turismo.print("user=", Turismo.param("id"));
        }

        @POST("/ctrl/items")
        void createItem() {
            Turismo.print("created");
        }

        @PUT("/ctrl/items")
        void updateItem() {
            Turismo.print("updated");
        }

        @DELETE("/ctrl/items")
        void deleteItem() {
            Turismo.print("deleted");
        }

        @PATCH("/ctrl/items")
        void patchItem() {
            Turismo.print("patched");
        }

        @GET("/ctrl/error")
        void error() {
            throw new RuntimeException("test error");
        }
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
