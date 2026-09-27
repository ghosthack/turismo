package io.github.ghosthack.turismo.multipart;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class MultipartParserTest {

    private static final String BOUNDARY = "XyZ";

    @AfterEach
    public void tearDown() {
        MultipartParser.setMaxContentSize(MultipartParser.DEFAULT_MAX_CONTENT_SIZE);
        MultipartParser.setMaxParts(MultipartParser.DEFAULT_MAX_PARTS);
    }

    /** Collects what the parser reports. */
    static class Collector implements Parametrizable {
        final Map<String, List<String>> fields = new HashMap<>();
        final Map<String, String[]> files = new HashMap<>();
        final Map<String, Object> attributes = new HashMap<>();

        @Override
        public void addParameter(String name, String value) {
            fields.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        @Override
        public void addParameter(String name, String[] value) {
            files.put(name, value);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attributes.put(name, value);
        }
    }

    private static Collector parse(String body) throws Exception {
        return parse(body.getBytes(StandardCharsets.UTF_8), "UTF-8");
    }

    private static Collector parse(byte[] body, String charset) throws Exception {
        Collector c = new Collector();
        new MultipartParser(new ByteArrayInputStream(body), "--" + BOUNDARY,
                c, charset, body.length).parse();
        return c;
    }

    @Test
    public void testFieldsAndFile() throws Exception {
        Collector c = parse("--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"title\"\r\n"
                + "\r\n"
                + "Hello\r\n"
                + "--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"a.png\"\r\n"
                + "Content-Type: image/png\r\n"
                + "\r\n"
                + "PNGDATA\r\n"
                + "--XyZ--\r\n");
        assertEquals(List.of("Hello"), c.fields.get("title"));
        assertArrayEquals(new String[] {"image/png", "a.png"}, c.files.get("image"));
        assertArrayEquals("PNGDATA".getBytes(StandardCharsets.US_ASCII),
                (byte[]) c.attributes.get("image"));
    }

    @Test
    public void testHeaderOrderCaseAndExtraHeaders() throws Exception {
        Collector c = parse("--XyZ\r\n"
                + "content-type: text/plain\r\n"
                + "X-Extra: 1\r\n"
                + "CONTENT-DISPOSITION: form-data; filename=\"n.txt\"; name=\"doc\"\r\n"
                + "\r\n"
                + "text\r\n"
                + "--XyZ--");
        assertArrayEquals(new String[] {"text/plain", "n.txt"}, c.files.get("doc"));
    }

    @Test
    public void testFileWithoutContentTypeDefaultsToOctetStream() throws Exception {
        Collector c = parse("--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"f\"; filename=\"x\"\r\n"
                + "\r\n"
                + "\r\n"
                + "--XyZ--");
        assertArrayEquals(new String[] {"application/octet-stream", "x"},
                c.files.get("f"));
        assertEquals(0, ((byte[]) c.attributes.get("f")).length);
    }

    @Test
    public void testFileContentWithLineBreaksAndBoundaryLikeBytes() throws Exception {
        String content = "line1\r\n--XY\r\nx--XyZ\r\n-\r\n--\r\nline3\r\n";
        Collector c = parse("--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"f\"; filename=\"x\"\r\n"
                + "\r\n"
                + content + "\r\n"
                + "--XyZ--\r\n");
        assertEquals(content, new String((byte[]) c.attributes.get("f"),
                StandardCharsets.US_ASCII));
    }

    @Test
    public void testPreambleTransportPaddingAndRepeatedFields() throws Exception {
        Collector c = parse("This is a preamble\r\n"
                + "--XyZ  \r\n"
                + "Content-Disposition: form-data; name=\"tag\"\r\n"
                + "\r\n"
                + "a\r\n"
                + "--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"tag\"\r\n"
                + "\r\n"
                + "b\r\n"
                + "--XyZ--\r\nepilogue");
        assertEquals(List.of("a", "b"), c.fields.get("tag"));
    }

    @Test
    public void testQuotedParamsWithSemicolonsAndEscapes() {
        Map<String, String> params = MultipartParser.parseParams(
                "form-data; name=\"a;b\"; filename=\"q%22uote%0d%0A.txt\"; x=plain");
        assertEquals("a;b", params.get("name"));
        assertEquals("q\"uote\r\n.txt", params.get("filename"));
        assertEquals("plain", params.get("x"));
    }

    @Test
    public void testBackslashInFileNameIsLiteral() {
        Map<String, String> params = MultipartParser.parseParams(
                "form-data; name=\"f\"; filename=\"a\\b.txt\"");
        assertEquals("a\\b.txt", params.get("filename"));
        // Other percent sequences and paths are passed through untouched
        params = MultipartParser.parseParams(
                "form-data; name=\"f\"; filename=\"../x%41%2\\\"");
        assertEquals("../x%41%2\\", params.get("filename"));
    }

    @Test
    public void testTooManyPartsRejected() throws Exception {
        MultipartParser.setMaxParts(2);
        String two = "--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"a\"\r\n\r\n1\r\n"
                + "--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"a\"\r\n\r\n2\r\n";
        assertEquals(List.of("1", "2"), parse(two + "--XyZ--").fields.get("a"));
        assertThrows(ContentTooLargeException.class, () -> parse(two
                + "--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"a\"\r\n\r\n3\r\n"
                + "--XyZ--"));
    }

    @Test
    public void testManyRepeatedFieldsParseInLinearTime() throws Exception {
        final int n = 200_000;
        MultipartParser.setMaxParts(n);
        MultipartParser.setMaxContentSize(32 * 1024 * 1024);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("--XyZ\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\nx\r\n");
        }
        byte[] body = sb.append("--XyZ--").toString()
                .getBytes(StandardCharsets.US_ASCII);
        MultipartRequest mr = assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(10), () -> {
                    MultipartRequest r = new MultipartRequest(request(
                            "multipart/form-data; boundary=XyZ", body,
                            body.length));
                    new MultipartParser(new ByteArrayInputStream(body),
                            "--XyZ", r, "UTF-8", body.length).parse();
                    return r;
                });
        assertEquals(n, mr.getParameterValues("a").length);
    }

    @Test
    public void testDecodesWithGivenCharset() throws Exception {
        String body = "--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"city\"\r\n"
                + "\r\n"
                + "São Paulo\r\n"
                + "--XyZ--";
        assertEquals(List.of("São Paulo"), parse(body).fields.get("city"));
        assertEquals(List.of("São Paulo"),
                parse(body.getBytes(StandardCharsets.ISO_8859_1), "ISO-8859-1")
                        .fields.get("city"));
    }

    @Test
    public void testMissingBoundary() throws Exception {
        assertThrows(ParseException.class, () -> {
            parse("no multipart here");
        });
    }

    @Test
    public void testUnterminatedPart() throws Exception {
        assertThrows(ParseException.class, () -> {
            parse("--XyZ\r\n"
                    + "Content-Disposition: form-data; name=\"a\"\r\n"
                    + "\r\n"
                    + "value without closing boundary");
        });
    }

    @Test
    public void testPartWithoutDisposition() throws Exception {
        assertThrows(ParseException.class, () -> {
            parse("--XyZ\r\nContent-Type: text/plain\r\n\r\nx\r\n--XyZ--");
        });
    }

    @Test
    public void testDeclaredSizeOverLimitRejectedBeforeReading() throws Exception {
        assertThrows(ContentTooLargeException.class, () -> {
            MultipartParser.setMaxContentSize(10);
            InputStream failing = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new AssertionError("body must not be read");
                }
            };
            new MultipartParser(failing, "--" + BOUNDARY, new Collector(),
                    "UTF-8", 11L).parse();
        });
    }

    @Test
    public void testActualSizeOverLimitRejectedWhenLengthUnknown() throws Exception {
        MultipartParser.setMaxContentSize(20);
        byte[] body = new byte[21];
        try {
            new MultipartParser(new ByteArrayInputStream(body), "--" + BOUNDARY,
                    new Collector(), "UTF-8", -1L).parse();
            fail("Expected ContentTooLargeException");
        } catch (ContentTooLargeException expected) {
            // good
        }
        // A declared length smaller than the real body does not help either
        try {
            new MultipartParser(new ByteArrayInputStream(body), "--" + BOUNDARY,
                    new Collector(), "UTF-8", 5L).parse();
            fail("Expected ContentTooLargeException");
        } catch (ContentTooLargeException expected) {
            // good
        }
    }

    // ---------------------------------------------------------------
    // MultipartRequest / MultipartFilter
    // ---------------------------------------------------------------

    private static final String SIMPLE_BODY = "--XyZ\r\n"
            + "Content-Disposition: form-data; name=\"city\"\r\n"
            + "\r\n"
            + "São Paulo\r\n"
            + "--XyZ--\r\n";

    private static HttpServletRequest request(String contentType, byte[] body,
            long contentLength) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn(contentType);
        when(req.getContentLengthLong()).thenReturn(contentLength);
        when(req.getInputStream()).thenReturn(servletStream(body));
        return req;
    }

    private static ServletInputStream servletStream(byte[] body) {
        ByteArrayInputStream in = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public int read() { return in.read(); }
            @Override public int read(byte[] b, int off, int len) {
                return in.read(b, off, len);
            }
            @Override public boolean isFinished() { return in.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener l) { }
        };
    }

    @Test
    public void testWrapAndParseDefaultsToUtf8AndAcceptsUnknownLength()
            throws Exception {
        byte[] body = SIMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        MultipartRequest mr = MultipartRequest.wrapAndParse(request(
                "multipart/form-data; boundary=\"XyZ\"", body, -1));
        assertEquals("São Paulo", mr.getParameter("city"));
    }

    @Test
    public void testWrapAndParseWithoutBoundary() throws Exception {
        assertThrows(ParseException.class, () -> {
            MultipartRequest.wrapAndParse(request("multipart/form-data",
                    new byte[0], 0));
        });
    }

    @Test
    public void testWrapAndParseDeclaredTooLarge() throws Exception {
        assertThrows(ContentTooLargeException.class, () -> {
            MultipartRequest.wrapAndParse(request(
                    "multipart/form-data; boundary=XyZ", new byte[0],
                    MultipartParser.getMaxContentSize() + 1L));
        });
    }

    @Test
    public void testWrapAndParseUnsupportedCharset() throws Exception {
        assertThrows(ParseException.class, () -> {
            byte[] body = SIMPLE_BODY.getBytes(StandardCharsets.UTF_8);
            HttpServletRequest req = request(
                    "multipart/form-data; boundary=XyZ", body, body.length);
            when(req.getCharacterEncoding()).thenReturn("no-such-charset");
            MultipartRequest.wrapAndParse(req);
        });
    }

    @Test
    public void testFilterSends400ForMalformedBody() throws Exception {
        byte[] body = "garbage".getBytes(StandardCharsets.US_ASCII);
        HttpServletRequest req = request(
                "multipart/form-data; boundary=XyZ", body, body.length);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        new MultipartFilter().doFilter(req, res, chain);
        verify(res).sendError(HttpServletResponse.SC_BAD_REQUEST);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    public void testFilterSends413ForTooLargeBody() throws Exception {
        HttpServletRequest req = request("multipart/form-data; boundary=XyZ",
                new byte[0], MultipartParser.getMaxContentSize() + 1L);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        new MultipartFilter().doFilter(req, res, chain);
        verify(res).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    public void testFilterPassesParsedRequestDown() throws Exception {
        byte[] body = SIMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        HttpServletRequest req = request(
                "multipart/form-data; boundary=XyZ", body, body.length);
        HttpServletResponse res = mock(HttpServletResponse.class);
        String[] seen = new String[1];
        new MultipartFilter().doFilter(req, res,
                (r, s) -> seen[0] = r.getParameter("city"));
        assertEquals("São Paulo", seen[0]);
    }

    @Test
    public void testFilterSends413ForTooManyParts() throws Exception {
        MultipartParser.setMaxParts(1);
        byte[] body = ("--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"a\"\r\n\r\n1\r\n"
                + "--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"b\"\r\n\r\n2\r\n"
                + "--XyZ--").getBytes(StandardCharsets.US_ASCII);
        HttpServletRequest req = request(
                "multipart/form-data; boundary=XyZ", body, body.length);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        new MultipartFilter().doFilter(req, res, chain);
        verify(res).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void testFilterSends413ForBodyOverLimitWithUnknownLength()
            throws Exception {
        MultipartParser.setMaxContentSize(20);
        HttpServletRequest req = request("multipart/form-data; boundary=XyZ",
                new byte[21], -1);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        new MultipartFilter().doFilter(req, res, chain);
        verify(res).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    public void testFilterSends400ForUnterminatedPart() throws Exception {
        byte[] body = ("--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"a\"\r\n\r\nno end")
                .getBytes(StandardCharsets.US_ASCII);
        HttpServletRequest req = request(
                "multipart/form-data; boundary=XyZ", body, body.length);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        new MultipartFilter().doFilter(req, res, chain);
        verify(res).sendError(HttpServletResponse.SC_BAD_REQUEST);
        verify(chain, never()).doFilter(any(), any());
    }

    private static final String FILES_BODY = "--XyZ\r\n"
            + "Content-Disposition: form-data; name=\"doc\"; filename=\"a.txt\"\r\n"
            + "Content-Type: text/plain\r\n"
            + "\r\n"
            + "AAA\r\n"
            + "--XyZ\r\n"
            + "Content-Disposition: form-data; name=\"doc\"; filename=\"b.bin\"\r\n"
            + "\r\n"
            + "BB\r\n"
            + "--XyZ\r\n"
            + "Content-Disposition: form-data; name=\"doc\"\r\n"
            + "\r\n"
            + "note\r\n"
            + "--XyZ\r\n"
            + "Content-Disposition: form-data; name=\"image\"; filename=\"i.png\"\r\n"
            + "Content-Type: image/png\r\n"
            + "\r\n"
            + "PNG\r\n"
            + "--XyZ--\r\n";

    private static MultipartRequest parseRequest(String body,
            HttpServletRequest req) throws Exception {
        MultipartRequest mr = new MultipartRequest(req);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        new MultipartParser(new ByteArrayInputStream(bytes), "--XyZ", mr,
                "UTF-8", bytes.length).parse();
        return mr;
    }

    @Test
    public void testRepeatedFilesAreKeptSeparateFromTextFields() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("multipart/form-data; boundary=XyZ");
        MultipartRequest mr = parseRequest(FILES_BODY, req);

        List<FilePart> docs = mr.getFiles("doc");
        assertEquals(2, docs.size());
        assertEquals("text/plain", docs.get(0).contentType());
        assertEquals("a.txt", docs.get(0).fileName());
        assertArrayEquals("AAA".getBytes(StandardCharsets.US_ASCII),
                docs.get(0).content());
        assertEquals("application/octet-stream", docs.get(1).contentType());
        assertEquals("b.bin", docs.get(1).fileName());
        assertArrayEquals("BB".getBytes(StandardCharsets.US_ASCII),
                docs.get(1).content());
        assertEquals("a.txt", mr.getFile("doc").fileName());
        // The text field is not mixed into the file metadata
        assertArrayEquals(new String[] {"note"}, mr.getParameterValues("doc"));

        // Single-file compatibility: [contentType, fileName] and attribute
        assertArrayEquals(new String[] {"image/png", "i.png"},
                mr.getParameterValues("image"));
        verify(req).setAttribute("image",
                mr.getFile("image").content());
        verify(req).setAttribute("doc", docs.get(0).content());
        verify(req, never()).setAttribute("doc", docs.get(1).content());

        assertEquals(List.of(), mr.getFiles("missing"));
        assertNull(mr.getFile("missing"));
        assertEquals(List.of("doc", "image"), List.copyOf(mr.getFileNames()));
    }

    @Test
    public void testQueryParametersMergedWithBodyFields() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContentType()).thenReturn("multipart/form-data; boundary=XyZ");
        when(req.getParameterValues("id")).thenReturn(new String[] {"5"});
        when(req.getParameterValues("tag")).thenReturn(new String[] {"q"});
        when(req.getParameterNames()).thenAnswer(inv ->
                java.util.Collections.enumeration(List.of("id", "tag")));
        MultipartRequest mr = parseRequest("--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"tag\"\r\n\r\nb\r\n"
                + "--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"title\"\r\n\r\nt\r\n"
                + "--XyZ--", req);

        assertEquals("5", mr.getParameter("id"));
        assertArrayEquals(new String[] {"q", "b"}, mr.getParameterValues("tag"));
        assertEquals("q", mr.getParameter("tag"));
        assertEquals("t", mr.getParameter("title"));
        assertNull(mr.getParameter("nope"));
        assertEquals(List.of("id", "tag", "title"),
                java.util.Collections.list(mr.getParameterNames()));
        Map<String, String[]> map = mr.getParameterMap();
        assertEquals(List.of("id", "tag", "title"), List.copyOf(map.keySet()));
        assertArrayEquals(new String[] {"q", "b"}, map.get("tag"));
    }
}
