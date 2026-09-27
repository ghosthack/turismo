package io.github.ghosthack.turismo.resolver;

import static io.github.ghosthack.turismo.HttpMocks.getRequestMock;
import static io.github.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.servlet.Env;

public class ListResolverTest {

    private ListResolver resolver;

    @BeforeEach
    public void setUp() {
        resolver = new ListResolver();
    }

    @AfterEach
    public void tearDown() {
        Env.destroy();
    }

    @Test
    public void testExactMatch() {
        final Runnable action = new Runnable() {
            @Override
            public void run() { }
        };
        resolver.route("GET", "/exact", action);

        HttpServletRequest req = getRequestMock("GET", "/exact");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertSame(action, resolver.resolve());
    }

    @Test
    public void testWildcardMatch() {
        final Runnable action = new Runnable() {
            @Override
            public void run() { }
        };
        resolver.route("GET", "/files/*/download", action);

        HttpServletRequest req = getRequestMock("GET", "/files/anything/download");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertSame(action, resolver.resolve());
    }

    @Test
    public void testNamedParamExtraction() {
        resolver.route("GET", "/users/:id", new Runnable() {
            @Override
            public void run() { }
        });

        HttpServletRequest req = getRequestMock("GET", "/users/123");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertNotNull(resolver.resolve());
        assertEquals("123", Env.params("id"));
    }

    @Test
    public void testMultipleParams() {
        resolver.route("GET", "/api/:version/users/:id", new Runnable() {
            @Override
            public void run() { }
        });

        HttpServletRequest req = getRequestMock("GET", "/api/v2/users/456");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertNotNull(resolver.resolve());
        assertEquals("v2", Env.params("version"));
        assertEquals("456", Env.params("id"));
    }

    @Test
    public void testWildcardAndParam() {
        resolver.route("GET", "/files/*/:name", new Runnable() {
            @Override
            public void run() { }
        });

        HttpServletRequest req = getRequestMock("GET", "/files/dir/report.pdf");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertNotNull(resolver.resolve());
        assertEquals("report.pdf", Env.params("name"));
    }

    @Test
    public void testNoMatchReturnsDefault() {
        final Runnable defaultAction = new Runnable() {
            @Override
            public void run() { }
        };
        resolver.route(defaultAction);
        resolver.route("GET", "/known", new Runnable() {
            @Override
            public void run() { }
        });

        HttpServletRequest req = getRequestMock("GET", "/unknown");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertSame(defaultAction, resolver.resolve());
    }

    @Test
    public void testNullDefaultReturnsNull() {
        resolver.route("GET", "/known", new Runnable() {
            @Override
            public void run() { }
        });

        HttpServletRequest req = getRequestMock("GET", "/unknown");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertNull(resolver.resolve());
    }

    @Test
    public void testPathLengthMismatch() {
        resolver.route("GET", "/a/b", new Runnable() {
            @Override
            public void run() { }
        });

        HttpServletRequest req = getRequestMock("GET", "/a/b/c");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertNull(resolver.resolve());
    }

    @Test
    public void testMethodMismatch() throws Exception {
        resolver.route("GET", "/path", new Runnable() {
            @Override
            public void run() { }
        });

        HttpServletRequest req = getRequestMock("POST", "/path");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        resolver.resolve().run();
        verify(res).setHeader("Allow", "GET, HEAD");
        verify(res).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    public void testHeadServedByGetRoute() {
        final Runnable action = () -> { };
        resolver.route("GET", "/users/:id", action);

        HttpServletRequest req = getRequestMock("HEAD", "/users/7");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertSame(action, resolver.resolve());
        assertEquals("7", Env.params("id"));
    }

    @Test
    public void testHeadRoutePreferredOverGet() {
        final Runnable get = () -> { };
        final Runnable head = () -> { };
        resolver.route("GET", "/x", get);
        resolver.route("HEAD", "/x", head);

        HttpServletRequest req = getRequestMock("HEAD", "/x");
        Env.create(req, getResponseMock(), null);

        assertSame(head, resolver.resolve());
    }

    @Test
    public void testRouteAliasing() {
        final Runnable action = new Runnable() {
            @Override
            public void run() { }
        };
        resolver.route("GET", "/original/:id", action);
        resolver.route("GET", "/alias/:id", "/original/:id");

        HttpServletRequest req = getRequestMock("GET", "/alias/77");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        Runnable resolved = resolver.resolve();
        assertSame(action, resolved);
        assertEquals("77", Env.params("id"));
    }

    @Test
    public void testRouteAliasingNonExistentTarget() {
        assertThrows(IllegalArgumentException.class, () -> {
            resolver.route("GET", "/source", new Runnable() {
                @Override
                public void run() { }
            });
            resolver.route("GET", "/alias", "/nonexistent");
        });
    }

    @Test
    public void testRouteAliasingNonExistentMethod() {
        assertThrows(IllegalArgumentException.class, () -> {
            resolver.route("GET", "/source", new Runnable() {
                @Override
                public void run() { }
            });
            resolver.route("POST", "/alias", "/source");
        });
    }

    @Test
    public void testParsedEntryNullPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ListResolver.ParsedEntry(new Runnable() {
                @Override
                public void run() { }
            }, null);
        });
    }
}
