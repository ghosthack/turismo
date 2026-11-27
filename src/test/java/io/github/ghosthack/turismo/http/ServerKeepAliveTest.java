package io.github.ghosthack.turismo.http;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.Turismo;

/**
 * An unread request body must not swallow the next request on a
 * keep-alive connection. A small declared length is discarded; a larger
 * one closes the socket instead of waiting for the client to finish.
 */
public class ServerKeepAliveTest {

    /** Above the JDK's ~64KB unread-body drain, below a typical socket buffer. */
    private static final int LARGE_BODY = 70 * 1024;

    private Server server;

    @BeforeEach
    public void setUp() throws IOException {
        server = new Server(Turismo.app(), "127.0.0.1", 0);
        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
        Turismo.reset();
    }

    @Test
    public void testPipelinedGetAfterGet() throws Exception {
        Turismo.get("/a", () -> Turismo.print("first"));
        Turismo.get("/b", () -> Turismo.print("second"));
        List<RawResponse> responses = pipeline(
                request("GET", "/a", null, null)
                + request("GET", "/b", null, null));
        assertEquals(2, responses.size());
        assertEquals(200, responses.get(0).status);
        assertEquals("first", responses.get(0).body);
        assertFalse(responses.get(0).close);
        assertEquals(200, responses.get(1).status);
        assertEquals("second", responses.get(1).body);
    }

    @Test
    public void testSmallUnreadBodyIsDiscardedAndNextRequestAnswered()
            throws Exception {
        Turismo.post("/f", () -> Turismo.print("posted"));
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        String body = "name=ada";
        List<RawResponse> responses = pipeline(
                request("POST", "/f", "text/plain", body)
                + request("GET", "/next", null, null));
        assertEquals(2, responses.size());
        assertEquals(200, responses.get(0).status);
        assertEquals("posted", responses.get(0).body);
        assertFalse(responses.get(0).close);
        assertEquals(200, responses.get(1).status);
        assertEquals("next-ok", responses.get(1).body);
    }

    @Test
    public void testSmallFormPostKeepsTheConnection() throws Exception {
        Turismo.post("/f", () -> Turismo.print("got " + Turismo.form("a")));
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        String body = "a=1";
        List<RawResponse> responses = pipeline(
                request("POST", "/f",
                        "application/x-www-form-urlencoded", body)
                + request("GET", "/next", null, null));
        assertEquals(2, responses.size());
        assertEquals(200, responses.get(0).status);
        assertEquals("got 1", responses.get(0).body);
        assertEquals("next-ok", responses.get(1).body);
    }

    @Test
    public void testMethodNotAllowedDiscardsASmallBody() throws Exception {
        Turismo.get("/only", () -> Turismo.print("get-ok"));
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        String body = "ignored";
        List<RawResponse> responses = pipeline(
                request("POST", "/only", "text/plain", body)
                + request("GET", "/next", null, null));
        assertEquals(2, responses.size());
        assertEquals(405, responses.get(0).status);
        assertEquals("Method Not Allowed", responses.get(0).body);
        assertFalse(responses.get(0).close);
        assertEquals("next-ok", responses.get(1).body);
    }

    @Test
    public void testNotFoundDiscardsASmallBody() throws Exception {
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        String body = "ignored";
        List<RawResponse> responses = pipeline(
                request("POST", "/missing", "text/plain", body)
                + request("GET", "/next", null, null));
        assertEquals(2, responses.size());
        assertEquals(404, responses.get(0).status);
        assertEquals("Not Found", responses.get(0).body);
        assertFalse(responses.get(0).close);
        assertEquals("next-ok", responses.get(1).body);
    }

    @Test
    public void testTooLargeFormClosesWithoutWaitingForTheBody()
            throws Exception {
        Turismo.app().setMaxFormSize(4);
        Turismo.post("/f", () -> Turismo.print("got " + Turismo.form("a")));
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        // Declared far above the form limit, and above what is safe to
        // discard. The follow-up is written where the body would be; the
        // server must answer 413 and drop the socket rather than wait
        // for the rest of the declared length.
        Exchange exchange = readUntilClosed(requestWithLength("POST", "/f",
                "application/x-www-form-urlencoded", 3_000_000, "a=1")
                + request("GET", "/next", null, null));
        assertEquals(1, exchange.responses.size());
        RawResponse tooLarge = exchange.responses.get(0);
        assertEquals(413, tooLarge.status);
        assertEquals("Content Too Large", tooLarge.body);
        assertTrue(tooLarge.close);
        assertNotEquals(Exchange.End.TIMEOUT, exchange.end);
        assertFalse(exchange.raw.contains("next-ok"));
    }

    @Test
    public void testTooLargeFormOfDiscardableLengthKeepsTheConnection()
            throws Exception {
        Turismo.app().setMaxFormSize(4);
        Turismo.post("/f", () -> Turismo.print("got " + Turismo.form("a")));
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        String body = "a=" + "x".repeat(98);
        List<RawResponse> responses = pipeline(
                request("POST", "/f",
                        "application/x-www-form-urlencoded", body)
                + request("GET", "/next", null, null));
        assertEquals(2, responses.size());
        assertEquals(413, responses.get(0).status);
        assertEquals("Content Too Large", responses.get(0).body);
        assertFalse(responses.get(0).close);
        assertEquals("next-ok", responses.get(1).body);
    }

    @Test
    public void testMethodNotAllowedClosesALargeUnreadBody() throws Exception {
        Turismo.get("/only", () -> Turismo.print("get-ok"));
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        String body = "x".repeat(LARGE_BODY);
        Exchange exchange = readUntilClosed(
                request("POST", "/only", "text/plain", body)
                + request("GET", "/next", null, null));
        assertEquals(1, exchange.responses.size());
        assertEquals(405, exchange.responses.get(0).status);
        assertEquals("Method Not Allowed", exchange.responses.get(0).body);
        assertTrue(exchange.responses.get(0).close);
        assertNotEquals(Exchange.End.TIMEOUT, exchange.end);
        assertFalse(exchange.raw.contains("next-ok"));
    }

    @Test
    public void testNotFoundClosesALargeUnreadBody() throws Exception {
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        String body = "x".repeat(LARGE_BODY);
        Exchange exchange = readUntilClosed(
                request("POST", "/missing", "text/plain", body)
                + request("GET", "/next", null, null));
        assertEquals(1, exchange.responses.size());
        assertEquals(404, exchange.responses.get(0).status);
        assertTrue(exchange.responses.get(0).close);
        assertNotEquals(Exchange.End.TIMEOUT, exchange.end);
        assertFalse(exchange.raw.contains("next-ok"));
    }

    @Test
    public void testSuccessfulHandlerClosesWhenItIgnoresALargeBody()
            throws Exception {
        Turismo.post("/f", () -> Turismo.print("posted"));
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        String body = "x".repeat(LARGE_BODY);
        Exchange exchange = readUntilClosed(
                request("POST", "/f", "text/plain", body)
                + request("GET", "/next", null, null));
        assertEquals(1, exchange.responses.size());
        assertEquals(200, exchange.responses.get(0).status);
        assertEquals("posted", exchange.responses.get(0).body);
        assertTrue(exchange.responses.get(0).close);
        assertNotEquals(Exchange.End.TIMEOUT, exchange.end);
        assertFalse(exchange.raw.contains("next-ok"));
    }

    @Test
    public void testFullyReadLargeBodyKeepsTheConnection() throws Exception {
        Turismo.post("/f", () -> {
            try {
                Turismo.body().readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Turismo.print("read");
        });
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        String body = "x".repeat(LARGE_BODY);
        List<RawResponse> responses = pipeline(
                request("POST", "/f", "text/plain", body)
                + request("GET", "/next", null, null));
        assertEquals(2, responses.size());
        assertEquals(200, responses.get(0).status);
        assertEquals("read", responses.get(0).body);
        assertFalse(responses.get(0).close);
        assertEquals("next-ok", responses.get(1).body);
    }

    @Test
    public void testPartialReadOfASmallBodyStillKeepsTheConnection()
            throws Exception {
        Turismo.post("/f", () -> {
            try {
                Turismo.body().readNBytes(2);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            Turismo.print("partial");
        });
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        List<RawResponse> responses = pipeline(
                request("POST", "/f", "text/plain", "hello")
                + request("GET", "/next", null, null));
        assertEquals(2, responses.size());
        assertEquals("partial", responses.get(0).body);
        assertFalse(responses.get(0).close);
        assertEquals("next-ok", responses.get(1).body);
    }

    @Test
    public void testEmptyResponseClosesALargeUnreadBody() throws Exception {
        Turismo.post("/f", () -> Turismo.status(204));
        String body = "x".repeat(LARGE_BODY);
        Exchange exchange = readUntilClosed(
                request("POST", "/f", "text/plain", body));
        assertEquals(1, exchange.responses.size());
        assertEquals(204, exchange.responses.get(0).status);
        assertTrue(exchange.responses.get(0).close);
        assertNotEquals(Exchange.End.TIMEOUT, exchange.end, exchange.raw);
    }

    @Test
    public void testEmptyResponseDoesNotWaitForABodyThatNeverArrives()
            throws Exception {
        Turismo.post("/f", () -> Turismo.status(204));
        Turismo.get("/next", () -> Turismo.print("next-ok"));
        Exchange exchange = readUntilClosed(requestWithLength(
                "POST", "/f", "text/plain", 3_000_000, "")
                + request("GET", "/next", null, null));
        assertEquals(1, exchange.responses.size());
        assertEquals(204, exchange.responses.get(0).status);
        assertTrue(exchange.responses.get(0).close, exchange.raw);
        assertNotEquals(Exchange.End.TIMEOUT, exchange.end, exchange.raw);
        assertFalse(exchange.raw.contains("next-ok"));
    }

    @Test
    public void testStreamingHandlerClosesALargeUnreadBody() throws Exception {
        Turismo.post("/f", () -> {
            try {
                Turismo.stream().write("streamed".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        String body = "x".repeat(LARGE_BODY);
        Exchange exchange = readUntilClosed(
                request("POST", "/f", "text/plain", body));
        assertEquals(1, exchange.responses.size());
        assertEquals(200, exchange.responses.get(0).status);
        assertEquals("streamed", exchange.responses.get(0).body);
        assertTrue(exchange.responses.get(0).close);
        assertNotEquals(Exchange.End.TIMEOUT, exchange.end);
    }

    // ---------------------------------------------------------------
    // Raw pipelined client
    // ---------------------------------------------------------------

    /** Reads until two responses have arrived or the socket goes quiet. */
    private List<RawResponse> pipeline(String raw) throws Exception {
        Exchange exchange = read(raw, false);
        assertNotEquals(Exchange.End.TIMEOUT, exchange.end, exchange.raw);
        return exchange.responses;
    }

    /** Reads until the server closes the socket, or the read times out. */
    private Exchange readUntilClosed(String raw) throws Exception {
        return read(raw, true);
    }

    /**
     * Writes {@code raw} and reads the replies. The body is written on
     * another thread so a server that closes early cannot deadlock a
     * large upload. {@code untilClosed} waits for EOF or a reset instead
     * of stopping at the second response.
     */
    private Exchange read(String raw, boolean untilClosed) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", server.port())) {
            socket.setSoTimeout(2_000);
            Thread writer = Thread.ofVirtual().start(() -> {
                try {
                    OutputStream out = socket.getOutputStream();
                    out.write(raw.getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } catch (IOException ignored) {
                    // The server dropped the socket on purpose
                }
            });
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream got = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            Exchange.End end = Exchange.End.TIMEOUT;
            try {
                while (untilClosed || parse(got.toByteArray()).size() < 2) {
                    int n = in.read(buf);
                    if (n < 0) {
                        end = Exchange.End.EOF;
                        break;
                    }
                    got.write(buf, 0, n);
                }
                if (end != Exchange.End.EOF) {
                    end = Exchange.End.COMPLETE;
                }
            } catch (SocketTimeoutException e) {
                end = Exchange.End.TIMEOUT;
            } catch (SocketException e) {
                end = Exchange.End.RESET;
            }
            writer.join(2_000);
            byte[] bytes = got.toByteArray();
            return new Exchange(parse(bytes), end,
                    new String(bytes, StandardCharsets.ISO_8859_1));
        }
    }

    private static String request(String method, String path,
            String type, String body) {
        int length = body == null ? 0 : body.length();
        return requestWithLength(method, path, type, length,
                body == null ? "" : body);
    }

    private static String requestWithLength(String method, String path,
            String type, int length, String sent) {
        StringBuilder raw = new StringBuilder();
        raw.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        raw.append("Host: localhost\r\n");
        if (type != null) {
            raw.append("Content-Type: ").append(type).append("\r\n");
        }
        if (length > 0 || "POST".equals(method)) {
            raw.append("Content-Length: ").append(length).append("\r\n");
        }
        raw.append("\r\n");
        raw.append(sent);
        return raw.toString();
    }

    private static List<RawResponse> parse(byte[] raw) {
        List<RawResponse> responses = new ArrayList<>();
        int offset = 0;
        while (offset < raw.length) {
            int headerEnd = indexOf(raw, offset, "\r\n\r\n");
            if (headerEnd < 0) {
                break;
            }
            String head = new String(raw, offset, headerEnd - offset,
                    StandardCharsets.ISO_8859_1);
            int lineEnd = head.indexOf("\r\n");
            String statusLine = lineEnd < 0 ? head : head.substring(0, lineEnd);
            String[] parts = statusLine.split(" ");
            if (parts.length < 2) {
                break;
            }
            int status = Integer.parseInt(parts[1]);
            boolean close = header(head, "Connection").stream()
                    .anyMatch(v -> v.equalsIgnoreCase("close"));
            String transfer = header(head, "Transfer-Encoding").stream()
                    .findFirst().orElse("");
            int bodyStart = headerEnd + 4;
            if (transfer.equalsIgnoreCase("chunked")) {
                Chunk chunk = readChunks(raw, bodyStart);
                if (chunk == null) {
                    break;
                }
                responses.add(new RawResponse(status, close, chunk.body));
                offset = chunk.next;
                continue;
            }
            int length = contentLength(head);
            if (raw.length < bodyStart + length) {
                break;
            }
            String body = new String(raw, bodyStart, length,
                    StandardCharsets.UTF_8);
            responses.add(new RawResponse(status, close, body));
            offset = bodyStart + length;
        }
        return responses;
    }

    /** The decoded chunked body, or null if the terminator is not here yet. */
    private static Chunk readChunks(byte[] raw, int offset) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int i = offset;
        while (true) {
            int line = indexOf(raw, i, "\r\n");
            if (line < 0) {
                return null;
            }
            String sizeText = new String(raw, i, line - i,
                    StandardCharsets.ISO_8859_1);
            int semi = sizeText.indexOf(';');
            if (semi >= 0) {
                sizeText = sizeText.substring(0, semi);
            }
            int size;
            try {
                size = Integer.parseInt(sizeText.trim(), 16);
            } catch (NumberFormatException e) {
                return null;
            }
            int data = line + 2;
            if (size == 0) {
                int end = indexOf(raw, data, "\r\n");
                if (end < 0) {
                    return null;
                }
                return new Chunk(body.toString(StandardCharsets.UTF_8),
                        end + 2);
            }
            if (raw.length < data + size + 2) {
                return null;
            }
            body.write(raw, data, size);
            i = data + size + 2;
        }
    }

    private static int contentLength(String head) {
        return header(head, "Content-Length").stream()
                .map(Integer::parseInt)
                .findFirst()
                .orElse(0);
    }

    private static List<String> header(String head, String name) {
        List<String> values = new ArrayList<>();
        for (String line : head.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            if (line.substring(0, colon).trim().equalsIgnoreCase(name)) {
                values.add(line.substring(colon + 1).trim());
            }
        }
        return values;
    }

    private static int indexOf(byte[] raw, int from, String needle) {
        byte[] n = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = from; i <= raw.length - n.length; i++) {
            for (int j = 0; j < n.length; j++) {
                if (raw[i + j] != n[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private record RawResponse(int status, boolean close, String body) { }

    private record Chunk(String body, int next) { }

    private record Exchange(List<RawResponse> responses, End end, String raw) {
        enum End { COMPLETE, EOF, RESET, TIMEOUT }
    }
}
