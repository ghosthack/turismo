package io.github.ghosthack.turismo;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.annotation.DELETE;
import io.github.ghosthack.turismo.annotation.GET;
import io.github.ghosthack.turismo.annotation.PATCH;
import io.github.ghosthack.turismo.annotation.Param;
import io.github.ghosthack.turismo.annotation.POST;
import io.github.ghosthack.turismo.annotation.PUT;

public class TurismoTest {

    @AfterEach
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

        App.RouteMatch match = Turismo.app().resolve("GET", "/hello");
        assertSame(action, match.action);
        assertTrue(match.params.isEmpty());
    }

    @Test
    public void testExactRouteMethodIsolation() {
        Runnable getAction = () -> {};
        Runnable postAction = () -> {};
        Turismo.get("/resource", getAction);
        Turismo.post("/resource", postAction);

        assertSame(getAction, Turismo.app().resolve("GET", "/resource").action);
        assertSame(postAction, Turismo.app().resolve("POST", "/resource").action);
    }

    @Test
    public void testNoMatchReturnsNotFoundHandler() {
        App.RouteMatch match = Turismo.app().resolve("GET", "/missing");
        assertNotNull(match.action);
        assertTrue(match.params.isEmpty());
    }

    @Test
    public void testPatternRouteWithParam() {
        Runnable action = () -> {};
        Turismo.get("/users/:id", action);

        App.RouteMatch match = Turismo.app().resolve("GET", "/users/42");
        assertSame(action, match.action);
        assertEquals("42", match.params.get("id"));
    }

    @Test
    public void testPatternRouteWithMultipleParams() {
        Runnable action = () -> {};
        Turismo.get("/users/:userId/posts/:postId", action);

        App.RouteMatch match = Turismo.app().resolve("GET", "/users/7/posts/99");
        assertSame(action, match.action);
        assertEquals("7", match.params.get("userId"));
        assertEquals("99", match.params.get("postId"));
    }

    @Test
    public void testWildcardRoute() {
        Runnable action = () -> {};
        Turismo.get("/files/*/download", action);

        App.RouteMatch match = Turismo.app().resolve("GET", "/files/report/download");
        assertSame(action, match.action);
        assertTrue(match.params.isEmpty());
    }

    @Test
    public void testPatternRouteMethodMismatch() {
        Turismo.get("/users/:id", () -> {});

        App.RouteMatch match = Turismo.app().resolve("POST", "/users/42");
        // Should fall through to not-found
        assertEquals(0, match.params.size());
    }

    @Test
    public void testPatternRouteSegmentCountMismatch() {
        Turismo.get("/users/:id", () -> {});

        // Too many segments
        App.RouteMatch match = Turismo.app().resolve("GET", "/users/42/extra");
        assertTrue(match.params.isEmpty());
    }

    @Test
    public void testExactRoutePriorityOverPattern() {
        Runnable exact = () -> {};
        Runnable pattern = () -> {};
        Turismo.get("/users/admin", exact);
        Turismo.get("/users/:id", pattern);

        // Exact match should win
        assertSame(exact, Turismo.app().resolve("GET", "/users/admin").action);
    }

    @Test
    public void testCustomNotFoundHandler() {
        Runnable custom = () -> {};
        Turismo.notFound(custom);

        assertSame(custom, Turismo.app().resolve("GET", "/whatever").action);
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

        assertSame(a, Turismo.app().resolve("GET", "/a").action);
        assertSame(a, Turismo.app().resolve("POST", "/a").action);
        assertSame(a, Turismo.app().resolve("PUT", "/a").action);
        assertSame(a, Turismo.app().resolve("DELETE", "/a").action);
        assertSame(a, Turismo.app().resolve("PATCH", "/a").action);
        assertSame(a, Turismo.app().resolve("HEAD", "/a").action);
        assertSame(a, Turismo.app().resolve("OPTIONS", "/a").action);
    }

    @Test
    public void testResetClearsRoutes() {
        Turismo.get("/hello", () -> {});
        Turismo.reset();

        // Should be not-found now
        App.RouteMatch match = Turismo.app().resolve("GET", "/hello");
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

    @Test
    public void testContextThrowsOutsideRequest() {
        assertThrows(IllegalStateException.class, () -> {
            Turismo.context();
        });
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

    @Test
    public void testRedirectRejectsCR() {
        assertThrows(IllegalArgumentException.class, () -> {
            Turismo.validateLocation("/bad\rlocation");
        });
    }

    @Test
    public void testRedirectRejectsLF() {
        assertThrows(IllegalArgumentException.class, () -> {
            Turismo.validateLocation("/bad\nlocation");
        });
    }

    @Test
    public void testRedirectRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            Turismo.validateLocation(null);
        });
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
        assertEquals(200, ctx.statusCode);
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
    // POST status
    // ---------------------------------------------------------------

    @Test
    public void testPostDefaultStatusIs200() {
        MockContext ctx = new MockContext("POST", "/items");
        Turismo.post("/items", () -> Turismo.print("done"));

        Turismo.handle(ctx);
        assertEquals(200, ctx.statusCode);
        assertEquals("done", ctx.printed.toString());
    }

    @Test
    public void testPostCanSetCreated() {
        MockContext ctx = new MockContext("POST", "/items");
        Turismo.post("/items", () -> { Turismo.status(201); Turismo.print("ok"); });

        Turismo.handle(ctx);
        assertEquals(201, ctx.statusCode);
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

    @Test
    public void testToJsonUnsupportedType() {
        assertThrows(IllegalArgumentException.class, () -> {
            Turismo.toJson(new Object());
        });
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

    @Test
    public void testPathPatternNullPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PathPattern(null);
        });
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
    public void testControllerPostDefaultStatusIs200() {
        MockContext ctx = new MockContext("POST", "/ctrl/items");
        Turismo.controller(new TestController());

        Turismo.handle(ctx);
        assertEquals(200, ctx.statusCode);
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

    @Test
    public void testControllerNoAnnotationsThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            Turismo.controller(new Object());
        });
    }

    // ---------------------------------------------------------------
    // Form bodies
    // ---------------------------------------------------------------

    @Test
    public void testFormField() {
        Turismo.post("/f", () -> Turismo.print(Turismo.form("name") + "|"
                + Turismo.form("note") + "|" + Turismo.form("missing")));
        MockContext ctx = new MockContext("POST", "/f")
                .form("name=Ana+Mar%C3%ADa&note=a%26b%3Dc");
        Turismo.handle(ctx);
        assertEquals("Ana María|a&b=c|null", ctx.printed.toString());
    }

    @Test
    public void testFormRawUtf8AndCharsetParameter() {
        Turismo.post("/f", () -> Turismo.print(Turismo.form("name")));
        MockContext utf8 = new MockContext("POST", "/f").form("name=José");
        Turismo.handle(utf8);
        assertEquals("José", utf8.printed.toString());

        MockContext latin1 = new MockContext("POST", "/f");
        latin1.requestHeaders.put("Content-Type",
                "application/x-www-form-urlencoded; charset=ISO-8859-1");
        latin1.requestBody = "name=Jos%E9".getBytes(StandardCharsets.US_ASCII);
        Turismo.handle(latin1);
        assertEquals("José", latin1.printed.toString());
    }

    @Test
    public void testFormsMapKeepsFirstValueAndSkipsEmptyPairs() {
        Turismo.post("/f", () -> Turismo.print(Turismo.formFields().toString()));
        MockContext ctx = new MockContext("POST", "/f").form("a=1&&b&a=2");
        Turismo.handle(ctx);
        assertEquals("{a=1, b=}", ctx.printed.toString());
    }

    @Test
    public void testParamFallsBackToForm() {
        Turismo.post("/f/:id", () -> Turismo.print(Turismo.param("id") + " "
                + Turismo.param("q") + " " + Turismo.param("name")));
        MockContext ctx = new MockContext("POST", "/f/7")
                .form("id=body&q=body&name=Ana");
        ctx.queryParams.put("q", "query");
        Turismo.handle(ctx);
        assertEquals("7 query Ana", ctx.printed.toString());
    }

    @Test
    public void testFormIgnoresOtherContentTypes() {
        Turismo.post("/f", () -> {
            Turismo.print(Turismo.form("a") + " " + Turismo.formFields().size()
                    + " ");
            try {
                Turismo.print(new String(Turismo.body().readAllBytes(),
                        StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        MockContext ctx = new MockContext("POST", "/f");
        ctx.requestHeaders.put("Content-Type", "application/json");
        ctx.requestBody = "a=1".getBytes(StandardCharsets.UTF_8);
        Turismo.handle(ctx);
        assertEquals("null 0 a=1", ctx.printed.toString());
    }

    @Test
    public void testRequestErrorReplacesPartialResponse() {
        App app = new App();
        app.setMaxFormSize(3);
        app.post("/f", () -> {
            Turismo.header("X-Partial", "1");
            Turismo.print("partial ");
            Turismo.form("a");
            Turismo.print("unreachable");
        });
        MockContext ctx = new MockContext("POST", "/f").form("a=123456");
        app.handle(ctx);
        assertEquals(413, ctx.statusCode);
        assertEquals("Content Too Large", ctx.printed.toString());
        assertNull(ctx.responseHeaders.get("X-Partial"));
    }

    @Test
    public void testFormAfterRawBodyReadFailsLoudly() {
        String[] seen = new String[2];
        Turismo.post("/raw", () -> {
            try {
                seen[0] = new String(Turismo.context().body().readAllBytes(),
                        StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            IllegalStateException e = assertThrows(
                    IllegalStateException.class, () -> Turismo.form("a"));
            seen[1] = e.getMessage();
        });
        MockContext ctx = new MockContext("POST", "/raw").form("a=1");
        ctx.singleUseBody = true;
        Turismo.handle(ctx);
        assertEquals("a=1", seen[0]);
        assertTrue(seen[1].contains("already read"), seen[1]);

        // Same through Turismo.body()
        Turismo.post("/body", () -> {
            try {
                Turismo.body().readAllBytes();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            assertThrows(IllegalStateException.class,
                    () -> Turismo.param("missing"));
        });
        MockContext ctx2 = new MockContext("POST", "/body").form("a=1");
        ctx2.singleUseBody = true;
        Turismo.handle(ctx2);
    }

    @Test
    public void testRawBodyReadDoesNotAffectNonFormRequests() {
        Turismo.post("/json", () -> {
            try {
                Turismo.context().body().readAllBytes();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            Turismo.print(String.valueOf(Turismo.param("q")));
        });
        MockContext ctx = new MockContext("POST", "/json");
        ctx.requestHeaders.put("Content-Type", "application/json");
        ctx.requestBody = "{}".getBytes(StandardCharsets.UTF_8);
        ctx.singleUseBody = true;
        Turismo.handle(ctx);
        assertEquals("null", ctx.printed.toString());
    }

    @Test
    public void testContextBodyReplaysAfterFormParsed() {
        Turismo.post("/f", () -> {
            Turismo.form("a");
            try {
                Turismo.print(new String(
                        Turismo.context().body().readAllBytes(),
                        StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        MockContext ctx = new MockContext("POST", "/f").form("a=1&b=2");
        ctx.singleUseBody = true;
        Turismo.handle(ctx);
        assertEquals("a=1&b=2", ctx.printed.toString());
    }

    @Test
    public void testBodyReadableAfterFormParsed() throws Exception {
        Turismo.post("/f", () -> {
            Turismo.form("a");
            try {
                Turismo.print(new String(Turismo.body().readAllBytes(),
                        StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        MockContext ctx = new MockContext("POST", "/f").form("a=1&b=2");
        Turismo.handle(ctx);
        assertEquals("a=1&b=2", ctx.printed.toString());
    }

    @Test
    public void testFormTooLargeIs413() {
        App app = new App();
        app.setMaxFormSize(8);
        app.post("/f", () -> Turismo.print("got " + Turismo.form("a")));
        MockContext ok = new MockContext("POST", "/f").form("a=123456");
        app.handle(ok);
        assertEquals("got 123456", ok.printed.toString());

        MockContext big = new MockContext("POST", "/f").form("a=1234567");
        app.handle(big);
        assertEquals(413, big.statusCode);
        assertEquals("Content Too Large", big.printed.toString());

        MockContext declared = new MockContext("POST", "/f").form("a=1");
        declared.requestHeaders.put("Content-Length", "100");
        app.handle(declared);
        assertEquals(413, declared.statusCode);
    }

    @Test
    public void testMalformedFormIs400() {
        Turismo.post("/f", () -> Turismo.print("got " + Turismo.form("a")));
        MockContext ctx = new MockContext("POST", "/f").form("a=%zz");
        Turismo.handle(ctx);
        assertEquals(400, ctx.statusCode);
        assertEquals("Bad Request: malformed form body",
                ctx.printed.toString());

        MockContext charset = new MockContext("POST", "/f").form("a=1");
        charset.requestHeaders.put("Content-Type",
                "application/x-www-form-urlencoded; charset=nope");
        Turismo.handle(charset);
        assertEquals(400, charset.statusCode);
    }

    @Test
    public void testSetMaxFormSizeRejectsOutOfRange() {
        App app = new App();
        assertThrows(IllegalArgumentException.class,
                () -> app.setMaxFormSize(-1));
        assertThrows(IllegalArgumentException.class,
                () -> app.setMaxFormSize(Integer.MAX_VALUE));
    }

    @Test
    public void testControllerBindsFormFields() {
        Turismo.controller(new ArgsController());
        MockContext ctx = new MockContext("POST", "/args/signup")
                .form("email=a%40b.c&age=30&newsletter=true");
        Turismo.handle(ctx);
        assertEquals("a@b.c 30 true", ctx.printed.toString());
    }

    // ---------------------------------------------------------------
    // HEAD, 405, encoded paths
    // ---------------------------------------------------------------

    @Test
    public void testHeadFallsBackToGet() {
        Runnable get = () -> {};
        Turismo.get("/hello", get);
        Turismo.get("/users/:id", get);
        assertSame(get, Turismo.app().resolve("HEAD", "/hello").action);
        assertSame(get, Turismo.app().resolve("HEAD", "/users/1").action);
    }

    @Test
    public void testExplicitHeadRouteWins() {
        Runnable head = () -> {};
        Turismo.get("/hello", () -> {});
        Turismo.head("/hello", head);
        assertSame(head, Turismo.app().resolve("HEAD", "/hello").action);
    }

    @Test
    public void testMethodNotAllowed() {
        Turismo.get("/users/:id", () -> {});
        Turismo.put("/users/:id", () -> {});
        MockContext ctx = new MockContext("POST", "/users/42");
        Turismo.handle(ctx);
        assertEquals(405, ctx.statusCode);
        assertEquals("GET, HEAD, PUT", ctx.responseHeaders.get("Allow"));
        assertEquals("Method Not Allowed", ctx.printed.toString());
    }

    @Test
    public void testMethodNotAllowedExactRoute() {
        Turismo.post("/items", () -> {});
        MockContext ctx = new MockContext("GET", "/items");
        Turismo.handle(ctx);
        assertEquals(405, ctx.statusCode);
        assertEquals("POST", ctx.responseHeaders.get("Allow"));
    }

    @Test
    public void testUnknownPathStillNotFound() {
        Turismo.get("/users/:id", () -> {});
        MockContext ctx = new MockContext("POST", "/other");
        Turismo.handle(ctx);
        assertEquals(404, ctx.statusCode);
    }

    @Test
    public void testEncodedSlashStaysInParam() {
        Turismo.get("/files/:name", () -> {});
        App.RouteMatch match =
                Turismo.app().resolve("GET", "/files/a/b", "/files/a%2Fb");
        assertEquals("a/b", match.params.get("name"));
    }

    @Test
    public void testRawPathSegmentsAreDecoded() {
        Turismo.get("/caf\u00e9/:name", () -> {});
        App.RouteMatch match = Turismo.app().resolve("GET",
                "/caf\u00e9/x y", "/caf%C3%A9/x%20y");
        assertEquals("x y", match.params.get("name"));
    }

    @Test
    public void testPercentDecode() {
        assertEquals("a/b", App.percentDecode("a%2Fb"));
        assertEquals("caf\u00e9", App.percentDecode("caf%C3%A9"));
        assertEquals("a+b", App.percentDecode("a+b"));
        assertEquals("100%", App.percentDecode("100%"));
        assertEquals("%zz", App.percentDecode("%zz"));
        assertEquals("%4", App.percentDecode("%4"));
    }

    @Test
    public void testEncodedSlashDoesNotMatchExactRoute() {
        Turismo.get("/admin/secret", () -> {});
        for (String raw : new String[] {"/admin%2Fsecret", "/admin%2fsecret"}) {
            MockContext ctx = new MockContext("GET", "/admin/secret");
            ctx.rawPath = raw;
            Turismo.handle(ctx);
            assertEquals(404, ctx.statusCode, raw);
        }
    }

    @Test
    public void testEncodedSlashDoesNotReportAllowedMethods() {
        Turismo.post("/admin/secret", () -> {});
        MockContext ctx = new MockContext("GET", "/admin/secret");
        ctx.rawPath = "/admin%2Fsecret";
        Turismo.handle(ctx);
        assertEquals(404, ctx.statusCode);
        assertNull(ctx.responseHeaders.get("Allow"));
    }

    @Test
    public void testExactRouteStillMatchesOtherEncodings() {
        Turismo.get("/caf\u00e9", () -> Turismo.print("ok"));
        MockContext ctx = new MockContext("GET", "/caf\u00e9");
        ctx.rawPath = "/caf%C3%A9";
        Turismo.handle(ctx);
        assertEquals("ok", ctx.printed.toString());
    }

    @Test
    public void testRouteValidation() {
        Runnable noop = () -> {};
        assertRejected(() -> Turismo.route(null, "/a", noop));
        assertRejected(() -> Turismo.route("", "/a", noop));
        assertRejected(() -> Turismo.route("GET", null, noop));
        assertRejected(() -> Turismo.route("GET", "a", noop));
        assertRejected(() -> Turismo.route("GET", "", noop));
        assertRejected(() -> Turismo.route("GET", "/a", null));
        assertRejected(() -> Turismo.get("/a", (Runnable) null));
        assertRejected(() -> Turismo.notFound(null));
    }

    private static void assertRejected(Runnable registration) {
        try {
            registration.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // good
        }
    }

    @Test
    public void testPatternMatchResultIsUnmodifiable() {
        PathPattern pattern = new PathPattern("/users/:id");
        Map<String, String> params = pattern.match("/users/1".split("/"));
        assertEquals("1", params.get("id"));
        try {
            params.put("id", "2");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // good
        }
        try {
            pattern.paramEntries().clear();
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // good
        }
        assertEquals("1", pattern.match("/users/1".split("/")).get("id"));
    }

    enum Color { RED }

    record Point(int x, String label, List<Integer> tags) { }

    record Empty() { }

    @Test
    public void testToJsonCharacterEnumAndRecord() {
        assertEquals("\"c\"", Turismo.toJson('c'));
        assertEquals("\"\\\"\"", Turismo.toJson('"'));
        assertEquals("\"RED\"", Turismo.toJson(Color.RED));
        assertEquals("{\"x\":1,\"label\":\"p\",\"tags\":[2,3]}",
                Turismo.toJson(new Point(1, "p", List.of(2, 3))));
        assertEquals("{}", Turismo.toJson(new Empty()));
    }

    @Test
    public void testToJsonAllPrimitiveArrays() {
        assertEquals("[1,2]", Turismo.toJson(new byte[] {1, 2}));
        assertEquals("[1,2]", Turismo.toJson(new short[] {1, 2}));
        assertEquals("[1,2]", Turismo.toJson(new int[] {1, 2}));
        assertEquals("[1,2]", Turismo.toJson(new long[] {1, 2}));
        assertEquals("[1.5,null]",
                Turismo.toJson(new float[] {1.5f, Float.NaN}));
        assertEquals("[1.5,null]",
                Turismo.toJson(new double[] {1.5, Double.POSITIVE_INFINITY}));
        assertEquals("[true,false]",
                Turismo.toJson(new boolean[] {true, false}));
        assertEquals("[\"a\",\"b\"]", Turismo.toJson(new char[] {'a', 'b'}));
        assertEquals("[[1],[2]]", Turismo.toJson(new int[][] {{1}, {2}}));
    }

    @Test
    public void testDuplicateParamNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> {
            Turismo.get("/a/:id/b/:id", () -> {});
        });
    }

    // ---------------------------------------------------------------
    // JSON non-finite numbers
    // ---------------------------------------------------------------

    @Test
    public void testJsonNonFiniteNumbersAreNull() {
        assertEquals("null", Turismo.toJson(Double.NaN));
        assertEquals("null", Turismo.toJson(Double.POSITIVE_INFINITY));
        assertEquals("null", Turismo.toJson(Float.NEGATIVE_INFINITY));
        assertEquals("[1.5,null]",
                Turismo.toJson(new double[] { 1.5, Double.NaN }));
        assertEquals("{\"v\":null}",
                Turismo.toJson(Collections.singletonMap("v", Double.NaN)));
        assertEquals("2.5", Turismo.toJson(2.5));
    }

    // ---------------------------------------------------------------
    // Server lifecycle
    // ---------------------------------------------------------------

    @Test
    public void testStartTwiceThrows() {
        Turismo.start(0);
        try {
            Turismo.start(0);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // first server keeps running
            assertTrue(Turismo.port() > 0);
        }
    }

    @Test
    public void testFailedStartDoesNotRetainServer() throws Exception {
        try (java.net.ServerSocket taken = new java.net.ServerSocket(0)) {
            try {
                Turismo.start(taken.getLocalPort());
                fail("Expected RuntimeException");
            } catch (RuntimeException expected) {
            }
        }
        try {
            Turismo.port();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // ---------------------------------------------------------------
    // Controller validation and inheritance
    // ---------------------------------------------------------------

    @Test
    public void testControllerRejectsUnsupportedParameterType() {
        try {
            Turismo.controller(new UnsupportedParamController());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("withParam"));
            assertTrue(e.getMessage().contains("java.util.List"));
        }
    }

    @Test
    public void testControllerRejectsEmptyParamName() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> Turismo.controller(new EmptyParamNameController()));
        assertTrue(e.getMessage().contains("@Param"));
    }

    @Test
    public void testControllerBindsAnnotatedPathParam() {
        Turismo.controller(new ArgsController());
        MockContext ctx = new MockContext("GET", "/args/item/42");
        Turismo.handle(ctx);
        assertEquals("item: 43", ctx.printed.toString());
    }

    @Test
    public void testControllerBindsParamByJavaName() {
        // Names come from -parameters (pom.xml); ParameterNamesTest covers
        // classes compiled without it
        Turismo.controller(new ArgsController());
        MockContext ctx = new MockContext("GET", "/args/named/abc");
        Turismo.handle(ctx);
        assertEquals("name=abc", ctx.printed.toString());
    }

    @Test
    public void testControllerBindsQueryParamsAndTypes() {
        Turismo.controller(new ArgsController());
        MockContext ctx = new MockContext("GET", "/args/types/RED");
        ctx.queryParams.put("n", "9000000000");
        ctx.queryParams.put("f", "true");
        ctx.queryParams.put("d", "1.5");
        ctx.queryParams.put("u", "123e4567-e89b-12d3-a456-426614174000");
        Turismo.handle(ctx);
        assertEquals("RED 9000000000 true 1.5 "
                + "123e4567-e89b-12d3-a456-426614174000",
                ctx.printed.toString());
    }

    @Test
    public void testControllerMissingBoxedParamIsNull() {
        Turismo.controller(new ArgsController());
        MockContext ctx = new MockContext("GET", "/args/optional");
        Turismo.handle(ctx);
        assertEquals("page=null q=null", ctx.printed.toString());
    }

    @Test
    public void testControllerMissingPrimitiveParamIs400() {
        Turismo.controller(new ArgsController());
        MockContext ctx = new MockContext("GET", "/args/required");
        Turismo.handle(ctx);
        assertEquals(400, ctx.statusCode);
        assertEquals("Bad Request: missing parameter 'limit'",
                ctx.printed.toString());
    }

    @Test
    public void testControllerInvalidParamIs400() {
        Turismo.controller(new ArgsController());
        MockContext ctx = new MockContext("GET", "/args/item/abc");
        Turismo.handle(ctx);
        assertEquals(400, ctx.statusCode);
        assertEquals("Bad Request: invalid value for parameter 'id'",
                ctx.printed.toString());
    }

    @Test
    public void testControllerInvalidEnumAndBooleanAre400() {
        Turismo.controller(new ArgsController());
        MockContext badEnum = new MockContext("GET", "/args/types/PURPLE");
        Turismo.handle(badEnum);
        assertEquals(400, badEnum.statusCode);
        MockContext badBool = new MockContext("GET", "/args/types/RED");
        badBool.queryParams.put("f", "yes");
        Turismo.handle(badBool);
        assertEquals(400, badBool.statusCode);
    }

    @Test
    public void testControllerBindsContextAndBody() {
        Turismo.controller(new ArgsController());
        MockContext ctx = new MockContext("POST", "/args/echo");
        Turismo.handle(ctx);
        assertEquals("POST body=true", ctx.printed.toString());
    }

    @Test
    public void testControllerIncludesInheritedRoutes() {
        Turismo.controller(new ChildController());
        MockContext base = new MockContext("GET", "/base");
        Turismo.handle(base);
        assertEquals("base", base.printed.toString());
        MockContext child = new MockContext("GET", "/child");
        Turismo.handle(child);
        assertEquals("child", child.printed.toString());
    }

    @Test
    public void testControllerAnnotatedOverrideReplacesParentRoute() {
        Turismo.controller(new ChildController());
        MockContext moved = new MockContext("GET", "/new");
        Turismo.handle(moved);
        assertEquals("overridden", moved.printed.toString());
        MockContext old = new MockContext("GET", "/old");
        Turismo.handle(old);
        assertEquals(404, old.statusCode);
    }

    @Test
    public void testControllerUnannotatedOverrideKeepsParentRoute() {
        Turismo.controller(new PlainOverrideController());
        MockContext ctx = new MockContext("GET", "/base");
        Turismo.handle(ctx);
        assertEquals("plain override", ctx.printed.toString());
    }

    static class UnsupportedParamController {
        @GET("/p")
        void withParam(List<String> s) {
        }
    }

    static class EmptyParamNameController {
        @GET("/p/:id")
        void withParam(@Param("") String id) {
        }
    }

    enum Shade { RED, GREEN }

    static class ArgsController {
        @GET("/args/item/:id")
        void item(@Param("id") int id) {
            Turismo.print("item: " + (id + 1));
        }

        @GET("/args/named/:name")
        void named(String name) {
            Turismo.print("name=", name);
        }

        @GET("/args/types/:color")
        void types(Shade color, @Param("n") long n, @Param("f") boolean f,
                @Param("d") double d, @Param("u") java.util.UUID u) {
            Turismo.print(color + " " + n + " " + f + " " + d + " " + u);
        }

        @GET("/args/optional")
        void optional(Integer page, String q) {
            Turismo.print("page=" + page + " q=" + q);
        }

        @GET("/args/required")
        void required(int limit) {
            Turismo.print("limit=" + limit);
        }

        @POST("/args/signup")
        void signup(String email, int age, boolean newsletter) {
            Turismo.print(email + " " + age + " " + newsletter);
        }

        @POST("/args/echo")
        void echo(Context ctx, InputStream body) {
            Turismo.print(ctx.method() + " body=" + (body != null));
        }
    }

    static class BaseController {
        @GET("/base")
        void base() {
            Turismo.print("base");
        }

        @GET("/old")
        void moved() {
            Turismo.print("old");
        }
    }

    static class ChildController extends BaseController {
        @GET("/child")
        void child() {
            Turismo.print("child");
        }

        @Override
        @GET("/new")
        void moved() {
            Turismo.print("overridden");
        }
    }

    static class PlainOverrideController extends BaseController {
        @Override
        void base() {
            Turismo.print("plain override");
        }
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
        byte[] requestBody = new byte[0];
        final Map<String, String> responseHeaders = new HashMap<>();
        final StringBuilder printed = new StringBuilder();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        int statusCode = 200;
        String rawPath;

        MockContext(String method, String path) {
            this.method = method;
            this.path = path;
        }

        boolean singleUseBody;
        private InputStream bodyStream;

        /** Sets a urlencoded form body with its Content-Type. */
        MockContext form(String body) {
            requestHeaders.put("Content-Type",
                    "application/x-www-form-urlencoded");
            requestBody = body.getBytes(StandardCharsets.UTF_8);
            return this;
        }

        @Override public String method() { return method; }
        @Override public String rawPath() { return rawPath; }
        @Override public String path() { return path; }
        @Override public String query(String name) { return queryParams.get(name); }
        @Override public String header(String name) { return requestHeaders.get(name); }
        @Override public InputStream body() {
            if (!singleUseBody) {
                return new ByteArrayInputStream(requestBody);
            }
            // Like a real request: one stream, consumed once
            if (bodyStream == null) {
                bodyStream = new ByteArrayInputStream(requestBody);
            }
            return bodyStream;
        }
        @Override public void reset() {
            printed.setLength(0);
            responseHeaders.clear();
            outputStream.reset();
        }
        @Override public void status(int code) { this.statusCode = code; }
        @Override public void header(String name, String value) { responseHeaders.put(name, value); }
        @Override public void print(String text) { printed.append(text); }
        @Override public OutputStream output() { return outputStream; }
    }
}
