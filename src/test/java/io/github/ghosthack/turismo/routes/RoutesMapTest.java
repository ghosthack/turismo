package io.github.ghosthack.turismo.routes;

import static io.github.ghosthack.turismo.HttpMocks.getRequestMock;
import static io.github.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.mockito.Mockito.verify;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.ghosthack.turismo.servlet.Env;

public class RoutesMapTest {

    private RoutesMap routes;

    @Before
    public void setUp() {
        routes = new RoutesMap() {
            @Override
            protected void map() {
                get("/", new Runnable() {
                    @Override
                    public void run(){
                        Env.res().setStatus(HttpServletResponse.SC_OK);
                    }
                });
                get("/foo", new Runnable() {
                    @Override
                    public void run() {
                        Env.res().setStatus(201);
                    }
                });
                put("/", new Runnable() {
                    @Override
                    public void run() {
                        Env.res().setStatus(202);
                    }
                });
                post("/bar", new Runnable() {
                    @Override
                    public void run() {
                        Env.res().setStatus(203);
                    }
                });
                route(new Runnable() {
                    @Override
                    public void run() {
                        Env.res().setStatus(HttpServletResponse.SC_NOT_FOUND);
                    }
                });
            }
        };
    }

    @After
    public void tearDown() {
        Env.destroy();
    }

    @Test
    public void testGET1() {
        HttpServletRequest req = getRequestMock("GET", "/");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void testGET2() {
        HttpServletRequest req = getRequestMock("GET", "/foo");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(201);
    }

    @Test
    public void testPUT() {
        HttpServletRequest req = getRequestMock("PUT", "/");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(202);
    }

    @Test
    public void testPOST() {
        HttpServletRequest req = getRequestMock("POST", "/bar");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(203);
    }

    @Test
    public void testNotFound() {
        HttpServletRequest req = getRequestMock("POST", "/everyThingElse");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testMethodAgnosticRouteFallthrough() {
        RoutesMap fallbackRoutes = new RoutesMap() {
            @Override
            protected void map() {
                // Register a method-agnostic route
                resolver.route(null, "/common", new Runnable() {
                    @Override
                    public void run() {
                        Env.res().setStatus(HttpServletResponse.SC_OK);
                    }
                });
                // Register a GET-specific route
                get("/specific", new Runnable() {
                    @Override
                    public void run() {
                        Env.res().setStatus(201);
                    }
                });
            }
        };

        // GET /common should fall through to the method-agnostic route
        HttpServletRequest req = getRequestMock("GET", "/common");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        fallbackRoutes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_OK);
    }
}
