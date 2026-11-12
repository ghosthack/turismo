package io.github.ghosthack.turismo;

import static io.github.ghosthack.turismo.Turismo.param;
import static io.github.ghosthack.turismo.Turismo.print;
import static org.junit.jupiter.api.Assertions.*;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.TurismoTest.MockContext;

public class AppTest {

    @AfterEach
    public void tearDown() {
        Turismo.reset();
    }

    @Test
    public void testAppsAreIsolatedFromEachOtherAndTheDefault() {
        App a = new App();
        App b = new App();
        a.get("/who", "a");
        b.get("/who", "b");
        Turismo.get("/who", "default");

        MockContext ctx = new MockContext("GET", "/who");
        a.handle(ctx);
        assertEquals("a", ctx.printed.toString());

        ctx = new MockContext("GET", "/who");
        b.handle(ctx);
        assertEquals("b", ctx.printed.toString());

        ctx = new MockContext("GET", "/who");
        Turismo.handle(ctx);
        assertEquals("default", ctx.printed.toString());

        ctx = new MockContext("GET", "/only-in-a");
        a.get("/only-in-a", "x");
        b.handle(ctx);
        assertEquals(404, ctx.statusCode);
    }

    @Test
    public void testStaticApiActsOnDefaultApp() {
        Turismo.get("/x", "x");
        MockContext ctx = new MockContext("GET", "/x");
        Turismo.app().handle(ctx);
        assertEquals("x", ctx.printed.toString());
    }

    @Test
    public void testNotFoundAndResetArePerApp() {
        App app = new App();
        app.notFound(() -> print("custom"));
        MockContext ctx = new MockContext("GET", "/nope");
        app.handle(ctx);
        assertEquals("custom", ctx.printed.toString());

        ctx = new MockContext("GET", "/nope");
        Turismo.handle(ctx);
        assertEquals("Not Found", ctx.printed.toString());

        app.get("/r", "r");
        app.reset();
        ctx = new MockContext("GET", "/r");
        app.handle(ctx);
        assertEquals(404, ctx.statusCode);
        assertEquals("Not Found", ctx.printed.toString());
    }

    @Test
    public void testNestedDispatchRestoresOuterContext() {
        App inner = new App();
        inner.get("/inner/:id", () -> print("inner=", param("id")));
        App outer = new App();
        MockContext innerCtx = new MockContext("GET", "/inner/7");
        outer.get("/outer/:id", () -> {
            inner.handle(innerCtx);
            print("outer=", param("id"));
        });

        MockContext outerCtx = new MockContext("GET", "/outer/1");
        outer.handle(outerCtx);
        assertEquals("inner=7", innerCtx.printed.toString());
        assertEquals("outer=1", outerCtx.printed.toString());
        assertThrows(IllegalStateException.class, Turismo::context);
    }

    @Test
    public void testTwoAppsServeOnTheirOwnPorts() throws Exception {
        App a = new App();
        App b = new App();
        a.get("/who", "a");
        b.get("/who", "b");
        a.start(0);
        try {
            b.start(0);
            try {
                assertNotEquals(a.port(), b.port());
                assertEquals("a", get(a.port(), "/who"));
                assertEquals("b", get(b.port(), "/who"));
            } finally {
                b.stop();
            }
        } finally {
            a.stop();
        }
        assertThrows(IllegalStateException.class, a::port);
    }

    @Test
    public void testStartTwiceRejected() {
        App app = new App();
        app.start(0);
        try {
            assertThrows(IllegalStateException.class, () -> app.start(0));
        } finally {
            app.stop();
        }
    }

    private static String get(int port, String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(
                "http://localhost:" + port + path).toURL().openConnection();
        try {
            assertEquals(200, conn.getResponseCode());
            return new String(conn.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }


    @Test
    public void testRouteMethodIsCaseInsensitive() {
        App app = new App();
        Runnable exact = () -> { };
        Runnable pattern = () -> { };
        app.route("get", "/lower", exact);
        app.route("Post", "/items/:id", pattern);
        assertSame(exact, app.resolve("GET", "/lower").action);
        assertSame(pattern, app.resolve("POST", "/items/7").action);
        // Same route as the upper-case spelling: it is replaced, not added
        Runnable replaced = () -> { };
        app.route("GET", "/lower", replaced);
        assertSame(replaced, app.resolve("GET", "/lower").action);
    }

    @Test
    public void testStartOnLoopbackOnly() throws Exception {
        App app = new App();
        app.get("/ping", "pong");
        app.start("127.0.0.1", 0);
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    java.net.URI.create("http://127.0.0.1:" + app.port()
                            + "/ping").toURL().openConnection();
            assertEquals(200, conn.getResponseCode());
            conn.disconnect();
            assertThrows(IllegalStateException.class,
                    () -> app.start("127.0.0.1", 0));
        } finally {
            app.stop();
        }
        assertThrows(RuntimeException.class,
                () -> app.start("no such host.invalid", 0));
        assertThrows(IllegalStateException.class, app::port);
    }
}
