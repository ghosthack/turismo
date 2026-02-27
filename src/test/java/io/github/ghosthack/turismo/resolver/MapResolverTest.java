package io.github.ghosthack.turismo.resolver;

import static io.github.ghosthack.turismo.HttpMocks.getRequestMock;
import static io.github.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.ghosthack.turismo.servlet.Env;

public class MapResolverTest {

    private MapResolver resolver;

    @Before
    public void setUp() {
        resolver = new MapResolver();
    }

    @After
    public void tearDown() {
        Env.destroy();
    }

    @Test
    public void testExactMethodAndPathMatch() {
        final Runnable action = new Runnable() {
            @Override
            public void run() {
                Env.res().setStatus(HttpServletResponse.SC_OK);
            }
        };
        resolver.route("GET", "/test", action);

        HttpServletRequest req = getRequestMock("GET", "/test");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        Runnable resolved = resolver.resolve();
        assertSame(action, resolved);
    }

    @Test
    public void testMethodMismatchReturnsNotFound() {
        final boolean[] called = { false };
        resolver.route(new Runnable() {
            @Override
            public void run() {
                called[0] = true;
            }
        });
        resolver.route("GET", "/test", new Runnable() {
            @Override
            public void run() { }
        });

        HttpServletRequest req = getRequestMock("POST", "/test");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        Runnable resolved = resolver.resolve();
        resolved.run();
        // Should have called the not-found route
        org.junit.Assert.assertTrue(called[0]);
    }

    @Test
    public void testMethodAgnosticRouteFallthrough() {
        final Runnable commonAction = new Runnable() {
            @Override
            public void run() {
                Env.res().setStatus(HttpServletResponse.SC_OK);
            }
        };
        // Register a method-agnostic route
        resolver.route(null, "/common", commonAction);
        // Register a GET-specific route for a different path
        resolver.route("GET", "/specific", new Runnable() {
            @Override
            public void run() { }
        });

        HttpServletRequest req = getRequestMock("GET", "/common");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        Runnable resolved = resolver.resolve();
        // Should fall through to the method-agnostic route
        assertSame(commonAction, resolved);
    }

    @Test
    public void testMethodSpecificTakesPrecedence() {
        final Runnable specificAction = new Runnable() {
            @Override
            public void run() { }
        };
        final Runnable genericAction = new Runnable() {
            @Override
            public void run() { }
        };

        resolver.route(null, "/path", genericAction);
        resolver.route("GET", "/path", specificAction);

        HttpServletRequest req = getRequestMock("GET", "/path");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        Runnable resolved = resolver.resolve();
        assertSame(specificAction, resolved);
    }

    @Test
    public void testNullNotFoundRoute() {
        // No default route set, no routes match
        MapResolver emptyResolver = new MapResolver();

        HttpServletRequest req = getRequestMock("GET", "/missing");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        Runnable resolved = emptyResolver.resolve();
        assertNull(resolved);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRouteAliasingNotSupported() {
        resolver.route("GET", "/from", "/to");
    }
}
