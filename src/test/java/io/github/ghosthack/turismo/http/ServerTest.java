package io.github.ghosthack.turismo.http;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Test;

import io.github.ghosthack.turismo.Turismo;

public class ServerTest {

    @After
    public void tearDown() {
        Turismo.reset();
    }

    @Test
    public void testGetRequest() throws Exception {
        Turismo.get("/hello", () -> Turismo.print("Hello World"));
        Server server = startServer();
        try {
            HttpResult result = fetch("GET",
                    "http://localhost:" + server.port() + "/hello");
            assertEquals(200, result.status);
            assertEquals("Hello World", result.body);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testPathParam() throws Exception {
        Turismo.get("/users/:id", () ->
                Turismo.print("user=" + Turismo.param("id")));
        Server server = startServer();
        try {
            HttpResult result = fetch("GET",
                    "http://localhost:" + server.port() + "/users/42");
            assertEquals(200, result.status);
            assertEquals("user=42", result.body);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testQueryParam() throws Exception {
        Turismo.get("/search", () ->
                Turismo.print("q=" + Turismo.param("q")));
        Server server = startServer();
        try {
            HttpResult result = fetch("GET",
                    "http://localhost:" + server.port() + "/search?q=turismo");
            assertEquals(200, result.status);
            assertEquals("q=turismo", result.body);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testPostRequest() throws Exception {
        Turismo.post("/data", () -> {
            Turismo.status(201);
            Turismo.print("created");
        });
        Server server = startServer();
        try {
            HttpResult result = fetch("POST",
                    "http://localhost:" + server.port() + "/data");
            assertEquals(201, result.status);
            assertEquals("created", result.body);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testNotFound() throws Exception {
        Server server = startServer();
        try {
            HttpResult result = fetch("GET",
                    "http://localhost:" + server.port() + "/missing");
            assertEquals(404, result.status);
            assertEquals("Not Found", result.body);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testRedirect() throws Exception {
        Turismo.get("/old", () -> Turismo.redirect("/new"));
        Server server = startServer();
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://localhost:" + server.port() + "/old").toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            assertEquals(302, conn.getResponseCode());
            assertEquals("/new", conn.getHeaderField("Location"));
            conn.disconnect();
        } finally {
            server.stop();
        }
    }

    @Test
    public void testResponseHeaders() throws Exception {
        Turismo.get("/json", () -> {
            Turismo.type("application/json");
            Turismo.print("{\"ok\":true}");
        });
        Server server = startServer();
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(
                    "http://localhost:" + server.port() + "/json").toURL().openConnection();
            assertEquals(200, conn.getResponseCode());
            assertEquals("application/json",
                    conn.getHeaderField("Content-Type"));
            String body = new String(
                    conn.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            assertEquals("{\"ok\":true}", body);
            conn.disconnect();
        } finally {
            server.stop();
        }
    }

    @Test
    public void testServerErrorOnException() throws Exception {
        Turismo.get("/boom", () -> {
            throw new RuntimeException("test error");
        });
        Server server = startServer();
        try {
            HttpResult result = fetch("GET",
                    "http://localhost:" + server.port() + "/boom");
            assertEquals(500, result.status);
            assertEquals("Internal Server Error", result.body);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testRandomPort() throws Exception {
        Server server = new Server(0);
        server.start();
        try {
            assertTrue(server.port() > 0);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testTurismoStartStop() throws Exception {
        Turismo.get("/ping", () -> Turismo.print("pong"));
        Turismo.start(0);
        try {
            int port = Turismo.port();
            assertTrue(port > 0);
            HttpResult result = fetch("GET",
                    "http://localhost:" + port + "/ping");
            assertEquals(200, result.status);
            assertEquals("pong", result.body);
        } finally {
            Turismo.stop();
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private Server startServer() throws IOException {
        Server server = new Server(0);
        server.start();
        return server;
    }

    private HttpResult fetch(String method, String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                URI.create(urlStr).toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setInstanceFollowRedirects(false);
        int status = conn.getResponseCode();
        String body;
        try {
            body = new String(
                    conn.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Error stream for 4xx/5xx
            body = conn.getErrorStream() != null
                    ? new String(conn.getErrorStream().readAllBytes(),
                            StandardCharsets.UTF_8)
                    : "";
        }
        conn.disconnect();
        return new HttpResult(status, body);
    }

    static class HttpResult {
        final int status;
        final String body;
        HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
