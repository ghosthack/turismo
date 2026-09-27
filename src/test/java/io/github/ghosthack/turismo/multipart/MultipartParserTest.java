package io.github.ghosthack.turismo.multipart;

import static org.junit.Assert.*;
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

import org.junit.After;
import org.junit.Test;

public class MultipartParserTest {

    private static final String BOUNDARY = "XyZ";

    @After
    public void tearDown() {
        MultipartParser.setMaxContentSize(MultipartParser.DEFAULT_MAX_CONTENT_SIZE);
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
                "form-data; name=\"a;b\"; filename=\"q\\\"uote.txt\"; x=plain");
        assertEquals("a;b", params.get("name"));
        assertEquals("q\"uote.txt", params.get("filename"));
        assertEquals("plain", params.get("x"));
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

    @Test(expected = ParseException.class)
    public void testMissingBoundary() throws Exception {
        parse("no multipart here");
    }

    @Test(expected = ParseException.class)
    public void testUnterminatedPart() throws Exception {
        parse("--XyZ\r\n"
                + "Content-Disposition: form-data; name=\"a\"\r\n"
                + "\r\n"
                + "value without closing boundary");
    }

    @Test(expected = ParseException.class)
    public void testPartWithoutDisposition() throws Exception {
        parse("--XyZ\r\nContent-Type: text/plain\r\n\r\nx\r\n--XyZ--");
    }

    @Test(expected = ContentTooLargeException.class)
    public void testDeclaredSizeOverLimitRejectedBeforeReading() throws Exception {
        MultipartParser.setMaxContentSize(10);
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new AssertionError("body must not be read");
            }
        };
        new MultipartParser(failing, "--" + BOUNDARY, new Collector(),
                "UTF-8", 11L).parse();
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

    @Test(expected = ParseException.class)
    public void testWrapAndParseWithoutBoundary() throws Exception {
        MultipartRequest.wrapAndParse(request("multipart/form-data",
                new byte[0], 0));
    }

    @Test(expected = ContentTooLargeException.class)
    public void testWrapAndParseDeclaredTooLarge() throws Exception {
        MultipartRequest.wrapAndParse(request(
                "multipart/form-data; boundary=XyZ", new byte[0],
                MultipartParser.getMaxContentSize() + 1L));
    }

    @Test(expected = ParseException.class)
    public void testWrapAndParseUnsupportedCharset() throws Exception {
        byte[] body = SIMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        HttpServletRequest req = request(
                "multipart/form-data; boundary=XyZ", body, body.length);
        when(req.getCharacterEncoding()).thenReturn("no-such-charset");
        MultipartRequest.wrapAndParse(req);
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
}
