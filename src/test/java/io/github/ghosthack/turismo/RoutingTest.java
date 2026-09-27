package io.github.ghosthack.turismo;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.TurismoTest.MockContext;
import io.github.ghosthack.turismo.annotation.GET;
import io.github.ghosthack.turismo.annotation.POST;
import io.github.ghosthack.turismo.other.CrossPackageController;

/**
 * Routing and controller registration: body binding order, overrides,
 * atomic registration, trailing slashes, re-registration, automatic
 * OPTIONS and strict floating-point arguments.
 */
public class RoutingTest {

    @AfterEach
    public void tearDown() {
        Turismo.reset();
    }

    private static MockContext handle(App app, String method, String path) {
        MockContext ctx = new MockContext(method, path);
        app.handle(ctx);
        return ctx;
    }

    // ---------------------------------------------------------------
    // InputStream arguments are bound after form parameters
    // ---------------------------------------------------------------

    static class UploadController {
        @POST("/upload")
        void up(InputStream in, String name) {
            try {
                Turismo.print(name + " "
                        + new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test
    public void testStreamArgumentBeforeFormParameter() {
        App app = new App();
        app.controller(new UploadController());
        MockContext ctx = new MockContext("POST", "/upload").form("name=bob");
        ctx.singleUseBody = true;
        app.handle(ctx);
        assertEquals(200, ctx.statusCode);
        assertEquals("bob name=bob", ctx.printed.toString());
    }

    @Test
    public void testFormAfterRawBodyIs400() {
        App app = new App();
        app.post("/raw", () -> {
            try {
                Turismo.body().readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Turismo.print(Turismo.param("name"));
        });
        MockContext ctx = new MockContext("POST", "/raw").form("name=bob");
        ctx.singleUseBody = true;
        app.handle(ctx);
        assertEquals(400, ctx.statusCode);
    }

    // ---------------------------------------------------------------
    // Overrides are matched by signature
    // ---------------------------------------------------------------

    static class OverloadBase {
        @GET("/item/:x")
        void item(int x) {
            Turismo.print("base " + x);
        }
    }

    static class AnnotatedOverload extends OverloadBase {
        @GET("/item")
        void item() {
            Turismo.print("sub");
        }
    }

    static class PlainOverload extends OverloadBase {
        void item(String s) {
        }
    }

    @Test
    public void testOverloadDoesNotHideSuperclassRoute() {
        App app = new App();
        app.controller(new AnnotatedOverload());
        assertEquals("base 3", handle(app, "GET", "/item/3").printed.toString());
        assertEquals("sub", handle(app, "GET", "/item").printed.toString());

        App plain = new App();
        plain.controller(new PlainOverload());
        assertEquals("base 3", handle(plain, "GET", "/item/3").printed.toString());
    }

    /** Superclass of {@link CrossPackageController}, in another package. */
    public static class PackageBase {
        @GET("/pp/base")
        void pp() {
            Turismo.print("base");
        }
    }

    @Test
    public void testPackagePrivateMethodNotOverriddenFromOtherPackage() {
        App app = new App();
        app.controller(new CrossPackageController());
        assertEquals("base", handle(app, "GET", "/pp/base").printed.toString());
        assertEquals("sub", handle(app, "GET", "/pp/sub").printed.toString());
    }

    // ---------------------------------------------------------------
    // controller() registers all routes or none
    // ---------------------------------------------------------------

    static class HalfBadController {
        @GET("/good")
        void good() {
            Turismo.print("good");
        }

        @GET("/bad")
        void bad(Map<String, String> unsupported) {
        }
    }

    static class BadPathController {
        @GET("/fine")
        void fine() {
        }

        @GET("no-slash")
        void broken() {
        }
    }

    @Test
    public void testControllerIsAtomic() {
        App app = new App();
        assertThrows(IllegalArgumentException.class,
                () -> app.controller(new HalfBadController()));
        assertEquals(404, handle(app, "GET", "/good").statusCode);

        assertThrows(IllegalArgumentException.class,
                () -> app.controller(new BadPathController()));
        assertEquals(404, handle(app, "GET", "/fine").statusCode);
    }

    // ---------------------------------------------------------------
    // Trailing and empty segments are significant
    // ---------------------------------------------------------------

    @Test
    public void testTrailingSlashIsSignificant() {
        App app = new App();
        app.get("/users/:id", () -> Turismo.print(Turismo.param("id")));
        app.get("/exact/", "exact");
        app.get("/files/*/view", "file");

        assertEquals("42", handle(app, "GET", "/users/42").printed.toString());
        assertEquals(404, handle(app, "GET", "/users/42/").statusCode);
        assertEquals(404, handle(app, "GET", "/users/42///").statusCode);
        assertEquals(404, handle(app, "GET", "/users/").statusCode);
        assertEquals(404, handle(app, "GET", "/users//").statusCode);

        assertEquals("exact", handle(app, "GET", "/exact/").printed.toString());
        assertEquals(404, handle(app, "GET", "/exact").statusCode);

        assertEquals(404, handle(app, "GET", "/files//view").statusCode);
        assertEquals("file", handle(app, "GET", "/files/a/view").printed.toString());

        // Same through the raw path
        MockContext ctx = new MockContext("GET", "/users/42/");
        ctx.rawPath = "/users/42/";
        app.handle(ctx);
        assertEquals(404, ctx.statusCode);
    }

    @Test
    public void testPatternWithTrailingSlash() {
        App app = new App();
        app.get("/dirs/:name/", () -> Turismo.print(Turismo.param("name")));
        assertEquals("a", handle(app, "GET", "/dirs/a/").printed.toString());
        assertEquals(404, handle(app, "GET", "/dirs/a").statusCode);
    }

    @Test
    public void testPathPatternSplitKeepsEmptySegments() {
        assertArrayEquals(new String[] {"", "a", ""}, PathPattern.split("/a/"));
        PathPattern p = new PathPattern("/users/:id");
        assertNull(p.match(PathPattern.split("/users/1/")));
        assertNull(p.match(PathPattern.split("/users/")));
        assertEquals("1", p.match(PathPattern.split("/users/1")).get("id"));
        // A root pattern still matches "/".split("/"), which is empty
        assertNotNull(new PathPattern("/").match("/".split("/")));
    }

    // ---------------------------------------------------------------
    // Re-registration: last wins for exact and pattern routes
    // ---------------------------------------------------------------

    @Test
    public void testReRegisteredPatternRouteReplacesOld() {
        App app = new App();
        app.get("/p/:id", "first");
        app.get("/p/*", "wildcard");
        app.get("/p/:id", "second");
        app.get("/e", "first");
        app.get("/e", "second");
        // Replaced in place: still ahead of the later wildcard route
        assertEquals("second", handle(app, "GET", "/p/1").printed.toString());
        assertEquals("second", handle(app, "GET", "/e").printed.toString());
        // Other methods are separate routes
        app.post("/p/:id", "post");
        assertEquals("second", handle(app, "GET", "/p/1").printed.toString());
    }

    @Test
    public void testConcurrentReRegistrationKeepsOneRoute() throws Exception {
        App app = new App();
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            String body = "t" + t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 200; i++) {
                    app.get("/c/:id", body);
                }
            });
            threads[t].start();
        }
        for (Thread t : threads) {
            t.join();
        }
        app.get("/c/:id", "final");
        assertEquals("final", handle(app, "GET", "/c/1").printed.toString());
    }

    // ---------------------------------------------------------------
    // Automatic OPTIONS
    // ---------------------------------------------------------------

    @Test
    public void testAutomaticOptions() {
        App app = new App();
        app.get("/users/:id", () -> { });
        app.delete("/users/:id", () -> { });
        app.post("/items", () -> { });

        MockContext ctx = handle(app, "OPTIONS", "/users/1");
        assertEquals(204, ctx.statusCode);
        assertEquals("DELETE, GET, HEAD, OPTIONS",
                ctx.responseHeaders.get("Allow"));
        assertEquals("", ctx.printed.toString());

        ctx = handle(app, "OPTIONS", "/items");
        assertEquals(204, ctx.statusCode);
        assertEquals("OPTIONS, POST", ctx.responseHeaders.get("Allow"));

        assertEquals(404, handle(app, "OPTIONS", "/nothing").statusCode);

        ctx = handle(app, "PUT", "/items");
        assertEquals(405, ctx.statusCode);
        assertEquals("OPTIONS, POST", ctx.responseHeaders.get("Allow"));
    }

    @Test
    public void testExplicitOptionsRouteWins() {
        App app = new App();
        app.get("/x", "x");
        app.options("/x", () -> Turismo.print("custom"));
        MockContext ctx = handle(app, "OPTIONS", "/x");
        assertEquals(200, ctx.statusCode);
        assertEquals("custom", ctx.printed.toString());
    }

    // ---------------------------------------------------------------
    // Floating-point arguments: plain decimal only
    // ---------------------------------------------------------------

    static class FloatController {
        @GET("/f")
        void f(Double d, Float f) {
            Turismo.print(d + " " + f);
        }
    }

    private static MockContext getFloat(String name, String value) {
        App app = new App();
        app.controller(new FloatController());
        MockContext ctx = new MockContext("GET", "/f");
        ctx.queryParams.put(name, value);
        app.handle(ctx);
        return ctx;
    }

    @Test
    public void testFloatArgumentsRejectNonDecimal() {
        for (String bad : new String[] {"NaN", "Infinity", "-Infinity",
                "0x1p3", "0X1.8P1", "1d", "1f", " 1", "1 ", "1e", ".", "",
                "1e400", "+", "1.5e+"}) {
            assertEquals(400, getFloat("d", bad).statusCode, "double " + bad);
            assertEquals(400, getFloat("f", bad).statusCode, "float " + bad);
        }
        assertEquals(400, getFloat("f", "1e39").statusCode);
    }

    @Test
    public void testFloatArgumentsAcceptDecimal() {
        assertEquals("1.5 null", getFloat("d", "1.5").printed.toString());
        assertEquals("-0.5 null", getFloat("d", "-.5").printed.toString());
        assertEquals("2.0E10 null", getFloat("d", "2e10").printed.toString());
        assertEquals("3.0 null", getFloat("d", "+3.").printed.toString());
        assertEquals("1.0E-5 null", getFloat("d", "1E-5").printed.toString());
        assertEquals("null 2.5", getFloat("f", "2.5").printed.toString());
    }
}
