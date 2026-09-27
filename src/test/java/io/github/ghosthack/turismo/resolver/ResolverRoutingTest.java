package io.github.ghosthack.turismo.resolver;

import static io.github.ghosthack.turismo.HttpMocks.getRequestMock;
import static io.github.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.servlet.Env;

/** Encoded slashes, HEAD/405 and concurrency for the servlet resolvers. */
public class ResolverRoutingTest {

    private static final Runnable NOT_FOUND = () -> { };

    @AfterEach
    public void tearDown() {
        Env.destroy();
    }

    private static HttpServletRequest encoded(String method, String decoded,
            String uri) {
        HttpServletRequest req = getRequestMock(method, decoded);
        when(req.getServletPath()).thenReturn("");
        when(req.getContextPath()).thenReturn("");
        when(req.getRequestURI()).thenReturn(uri);
        return req;
    }

    @Test
    public void testMapResolverEncodedSlashIsNotFound() {
        MapResolver resolver = new MapResolver();
        resolver.route(NOT_FOUND);
        Runnable secret = () -> { };
        resolver.route("GET", "/admin/secret", secret);
        resolver.route("/admin/secret", secret);

        Env.create(encoded("GET", "/admin/secret", "/admin%2Fsecret"),
                getResponseMock(), null);
        assertSame(NOT_FOUND, resolver.resolve());

        // The plain path still works
        Env.create(encoded("GET", "/admin/secret", "/admin/secret"),
                getResponseMock(), null);
        assertSame(secret, resolver.resolve());
    }

    @Test
    public void testListResolverEncodedSlashIsNotFound() {
        ListResolver resolver = new ListResolver();
        resolver.route(NOT_FOUND);
        resolver.route("GET", "/admin/secret", () -> { });
        resolver.route("GET", "/admin/*", () -> { });

        Env.create(encoded("GET", "/admin/secret", "/admin%2fsecret"),
                getResponseMock(), null);

        assertSame(NOT_FOUND, resolver.resolve());
    }

    @Test
    public void testListResolverEncodedSlashInParam() {
        ListResolver resolver = new ListResolver();
        resolver.route(NOT_FOUND);
        Runnable file = () -> { };
        resolver.route("GET", "/files/:name", file);

        Env.create(encoded("GET", "/files/a/b", "/files/a%2Fb%20c"),
                getResponseMock(), null);

        assertSame(file, resolver.resolve());
        assertEquals("a/b c", Env.params("name"));
    }

    @Test
    public void testListResolverEncodedHeadAnd405() throws Exception {
        ListResolver resolver = new ListResolver();
        resolver.route(NOT_FOUND);
        Runnable file = () -> { };
        resolver.route("GET", "/files/:name", file);

        Env.create(encoded("HEAD", "/files/a/b", "/files/a%2Fb"),
                getResponseMock(), null);
        assertSame(file, resolver.resolve());

        HttpServletResponse res = getResponseMock();
        Env.create(encoded("DELETE", "/files/a/b", "/files/a%2Fb"), res, null);
        resolver.resolve().run();
        verify(res).setHeader("Allow", "GET, HEAD");
        verify(res).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    public void testCustomResolverRejectsEncodedSlash() throws Exception {
        Runnable secret = () -> { };
        MethodPathResolver resolver = new MethodPathResolver() {
            @Override
            protected Runnable resolve(String method, String path) {
                return secret;
            }
            @Override public void route(String m, String p, Runnable r) { }
            @Override public void route(String m, String f, String t) { }
            @Override public void route(Runnable r) { }
        };
        HttpServletResponse res = getResponseMock();
        Env.create(encoded("GET", "/admin/secret", "/admin%2Fsecret"), res, null);

        Runnable resolved = resolver.resolve();
        resolved.run();

        assertFalse(resolved == secret);
        verify(res).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testSegmentsSkipServletPath() {
        HttpServletRequest req = getRequestMock("GET", "/files/a/b");
        when(req.getContextPath()).thenReturn("/ctx");
        when(req.getServletPath()).thenReturn("/app/v1");
        when(req.getRequestURI()).thenReturn("/ctx/app/v1/files/a%2Fb;x=1");

        assertArrayEquals(new String[] { "", "files", "a/b" },
                EncodedPath.segments(req, true));
    }

    @Test
    public void testSegmentsWithoutEncodedSlash() {
        HttpServletRequest req = getRequestMock("GET", "/a b");
        when(req.getRequestURI()).thenReturn("/a%20b");

        assertNull(EncodedPath.segments(req, true));
        assertFalse(EncodedPath.hasEncodedSlash(null));
        assertTrue(EncodedPath.hasEncodedSlash("/a%2f"));
        assertEquals("100%", EncodedPath.percentDecode("100%"));
        assertEquals("a+b", EncodedPath.percentDecode("a+b"));
        assertEquals("é", EncodedPath.percentDecode("%C3%A9"));
    }

    @Test
    public void testMapResolverRejectsNullPathAndAction() {
        MapResolver resolver = new MapResolver();
        assertThrows(IllegalArgumentException.class,
                () -> resolver.route("GET", null, () -> { }));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.route("GET", "/x", (Runnable) null));
    }

    @Test
    public void testListResolverRejectsNullMethod() {
        ListResolver resolver = new ListResolver();
        assertThrows(IllegalArgumentException.class,
                () -> resolver.route(null, "/x", () -> { }));
    }

    @Test
    public void testConcurrentRegistrationAndResolution() throws Exception {
        final MapResolver map = new MapResolver();
        final ListResolver list = new ListResolver();
        map.route(NOT_FOUND);
        list.route(NOT_FOUND);
        final int threads = 8;
        final int routes = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                final int id = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    Env.create(getRequestMock("GET", "/r/0/0"),
                            getResponseMock(), null);
                    try {
                        for (int i = 0; i < routes; i++) {
                            String path = "/r/" + id + "/" + i;
                            map.route("GET", path, () -> { });
                            list.route("GET", path, () -> { });
                            // Resolving while other threads register
                            map.resolve("GET", "/r/0/0");
                            list.resolve("GET", "/r/0/0");
                        }
                    } finally {
                        Env.destroy();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        Env.create(getRequestMock("GET", "/"), getResponseMock(), null);
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < routes; i++) {
                String path = "/r/" + t + "/" + i;
                assertFalse(map.resolve("GET", path) == NOT_FOUND, path);
                assertFalse(list.resolve("GET", path) == NOT_FOUND, path);
            }
        }
    }

}
