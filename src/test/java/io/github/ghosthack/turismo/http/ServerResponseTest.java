package io.github.ghosthack.turismo.http;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.App;
import io.github.ghosthack.turismo.Context;
import io.github.ghosthack.turismo.Turismo;

/**
 * Response handling of the embedded server: status validation, HEAD
 * lengths, streaming, redirects and error logging.
 */
public class ServerResponseTest {

    private Server server;
    private String base;

    @BeforeEach
    public void setUp() throws IOException {
        server = new Server(0);
        server.start();
        base = "http://localhost:" + server.port();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
        Turismo.reset();
    }

    // ---------------------------------------------------------------
    // Status codes
    // ---------------------------------------------------------------

    @Test
    public void testInvalidStatusCodesAreRejected() throws Exception {
        List<String> caught = Collections.synchronizedList(new ArrayList<>());
        Turismo.get("/status", () -> {
            int code = Integer.parseInt(Turismo.param("code"));
            try {
                Turismo.status(code);
            } catch (IllegalArgumentException e) {
                caught.add(Turismo.param("code"));
                throw e;
            }
        });
        for (int code : new int[] {0, 1000, 101, 199, 600, -1}) {
            HttpURLConnection conn = open("/status?code=" + code);
            assertEquals(500, conn.getResponseCode(), "code " + code);
            conn.disconnect();
        }
        assertEquals(6, caught.size());
        // The connection still works after a rejected 101
        HttpURLConnection conn = open("/status?code=299");
        assertEquals(299, conn.getResponseCode());
        conn.disconnect();
    }

    // ---------------------------------------------------------------
    // HEAD
    // ---------------------------------------------------------------

    @Test
    public void testHeadHasContentLengthOfGet() throws Exception {
        Turismo.get("/hello", () -> Turismo.print("Hello World"));
        HttpURLConnection conn = open("/hello");
        conn.setRequestMethod("HEAD");
        assertEquals(200, conn.getResponseCode());
        assertEquals("11", conn.getHeaderField("Content-Length"));
        assertEquals(0, conn.getInputStream().readAllBytes().length);
        conn.disconnect();
        // The connection is reusable: the length did not make the
        // client wait for a body
        HttpURLConnection get = open("/hello");
        assertEquals("Hello World", read(get.getInputStream()));
        get.disconnect();
    }

    // ---------------------------------------------------------------
    // Streaming
    // ---------------------------------------------------------------

    @Test
    public void testStreamingSendsChunkedBody() throws Exception {
        int size = 1 << 20;
        Turismo.get("/big", () -> {
            Turismo.type("application/octet-stream");
            Turismo.print("start:");
            OutputStream out = Turismo.stream();
            assertSame(out, Turismo.stream());
            assertSame(out, Turismo.output());
            byte[] chunk = new byte[8192];
            java.util.Arrays.fill(chunk, (byte) 'x');
            try {
                for (int i = 0; i < size / chunk.length; i++) {
                    out.write(chunk);
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            Turismo.print(":end");
        });
        HttpURLConnection conn = open("/big");
        assertEquals(200, conn.getResponseCode());
        assertEquals("chunked", conn.getHeaderField("Transfer-Encoding"));
        assertNull(conn.getHeaderField("Content-Length"));
        assertEquals("application/octet-stream",
                conn.getHeaderField("Content-Type"));
        String body = read(conn.getInputStream());
        assertEquals(6 + size + 4, body.length());
        assertTrue(body.startsWith("start:x"));
        assertTrue(body.endsWith("x:end"));
        conn.disconnect();
    }

    @Test
    public void testStatusAndHeadersFixedOnceStreaming() throws Exception {
        Turismo.get("/fixed", () -> {
            Turismo.status(201);
            Turismo.stream();
            Turismo.print(threw(() -> Turismo.status(500)) + ","
                    + threw(() -> Turismo.header("X-Late", "1")));
        });
        HttpURLConnection conn = open("/fixed");
        assertEquals(201, conn.getResponseCode());
        assertNull(conn.getHeaderField("X-Late"));
        assertEquals("IllegalStateException,IllegalStateException",
                read(conn.getInputStream()));
        conn.disconnect();
    }

    @Test
    public void testErrorAfterStreamingIsLoggedAndEndsResponse()
            throws Exception {
        List<LogRecord> records = captureLog(() -> {
            Turismo.get("/partial", () -> {
                Turismo.stream();
                Turismo.print("partial");
                throw new IllegalStateException("fail midway");
            });
            HttpURLConnection conn = open("/partial");
            // Status was already sent, so it can't become a 500
            assertEquals(200, conn.getResponseCode());
            assertEquals("partial", read(conn.getInputStream()));
            conn.disconnect();
        });
        assertEquals(1, records.size());
        assertEquals("fail midway", records.get(0).getThrown().getMessage());
    }

    @Test
    public void testStreamingHeadSendsNoBody() throws Exception {
        Turismo.get("/s", () -> {
            Turismo.stream();
            Turismo.print("ignored");
        });
        HttpURLConnection conn = open("/s");
        conn.setRequestMethod("HEAD");
        assertEquals(200, conn.getResponseCode());
        assertEquals(0, conn.getInputStream().readAllBytes().length);
        conn.disconnect();
    }

    @Test
    public void testStreamRequiresEmbeddedServer() {
        App app = new App();
        List<String> seen = new ArrayList<>();
        app.get("/x", () -> seen.add(threw(Turismo::stream)));
        app.handle(new StubContext("GET", "/x"));
        assertEquals(List.of("UnsupportedOperationException"), seen);
    }

    // ---------------------------------------------------------------
    // Redirects
    // ---------------------------------------------------------------

    @Test
    public void testRedirectWithCustomCode() throws Exception {
        Turismo.post("/move", () -> Turismo.redirect(307, "/elsewhere"));
        HttpURLConnection conn = open("/move");
        conn.setRequestMethod("POST");
        assertEquals(307, conn.getResponseCode());
        assertEquals("/elsewhere", conn.getHeaderField("Location"));
        conn.disconnect();
    }

    @Test
    public void testRedirectRejectsCrLf() throws Exception {
        Turismo.get("/go", () -> Turismo.redirect(Turismo.param("to")));
        List<LogRecord> records = captureLog(() -> {
            HttpURLConnection conn = open(
                    "/go?to=/a%0D%0ASet-Cookie:%20x=1");
            assertEquals(500, conn.getResponseCode());
            assertNull(conn.getHeaderField("Location"));
            assertNull(conn.getHeaderField("Set-Cookie"));
            conn.disconnect();
        });
        assertEquals(1, records.size());
        assertInstanceOf(IllegalArgumentException.class,
                records.get(0).getThrown());
    }

    @Test
    public void testRedirectLocal() throws Exception {
        Turismo.get("/login", () -> Turismo.redirectLocal(Turismo.param("next")));
        Turismo.post("/login", () ->
                Turismo.redirectLocal(303, Turismo.param("next")));
        HttpURLConnection conn = open("/login?next=/account%3Fa%3D1");
        assertEquals(302, conn.getResponseCode());
        assertEquals("/account?a=1", conn.getHeaderField("Location"));
        conn.disconnect();

        conn = open("/login?next=/home");
        conn.setRequestMethod("POST");
        assertEquals(303, conn.getResponseCode());
        assertEquals("/home", conn.getHeaderField("Location"));
        conn.disconnect();

        captureLog(() -> {
            for (String next : new String[] {"//evil.com", "/%5Cevil.com",
                    "/%09/evil.com", "https://x"}) {
                HttpURLConnection bad = open("/login?next=" + next);
                assertEquals(500, bad.getResponseCode(), next);
                assertNull(bad.getHeaderField("Location"), next);
                bad.disconnect();
            }
        });
    }

    // ---------------------------------------------------------------
    // Error logging
    // ---------------------------------------------------------------

    @Test
    public void testErrorLogDoesNotDecodePath() throws Exception {
        Turismo.get("/boom/*", () -> {
            throw new RuntimeException("boom");
        });
        List<LogRecord> records = captureLog(() -> {
            HttpURLConnection conn = open(
                    "/boom/x%0A2026-01-01%20INFO%20forged");
            assertEquals(500, conn.getResponseCode());
            conn.disconnect();
        });
        assertEquals(1, records.size());
        String message = records.get(0).getMessage();
        assertFalse(message.contains("\n"), message);
        assertTrue(message.contains("/boom/x%0A2026"), message);
    }

    @Test
    public void testEscapeControlChars() {
        assertEquals("/a", Server.escape("/a"));
        assertEquals("/a\\u000ab\\u000d\\u007f", Server.escape("/a\nb\r\u007f"));
        assertEquals("null", Server.escape(null));
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    interface IoTask {
        void run() throws Exception;
    }

    /** Runs the task, collecting what the server logs meanwhile. */
    private static List<LogRecord> captureLog(IoTask task) throws Exception {
        Logger logger = Logger.getLogger(Server.class.getName());
        List<LogRecord> records =
                Collections.synchronizedList(new ArrayList<>());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { records.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        boolean parent = logger.getUseParentHandlers();
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        try {
            task.run();
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(parent);
        }
        return records;
    }

    private static String threw(Runnable r) {
        try {
            r.run();
            return "none";
        } catch (RuntimeException e) {
            return e.getClass().getSimpleName();
        }
    }

    private HttpURLConnection open(String path) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)
                URI.create(base + path).toURL().openConnection();
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    private static String read(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    /** A minimal non-embedded transport. */
    static class StubContext implements Context {
        private final String method;
        private final String path;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        StubContext(String method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override public String method() { return method; }
        @Override public String path() { return path; }
        @Override public String query(String name) { return null; }
        @Override public String header(String name) { return null; }
        @Override public InputStream body() {
            return new ByteArrayInputStream(new byte[0]);
        }
        @Override public void status(int code) { }
        @Override public void header(String name, String value) { }
        @Override public void print(String text) {
            out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        }
        @Override public OutputStream output() { return out; }
    }
}
