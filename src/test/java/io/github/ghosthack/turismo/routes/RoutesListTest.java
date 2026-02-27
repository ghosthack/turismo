package io.github.ghosthack.turismo.routes;

import static io.github.ghosthack.turismo.HttpMocks.getRequestMock;
import static io.github.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.github.ghosthack.turismo.servlet.Env;

public class RoutesListTest {

    private RoutesList routes;

    @Before
    public void setUp() {
        routes = new RoutesList() {
            @Override
            protected void map() {
                get("/", new Runnable() {
                    @Override
                    public void run() {
                        Env.res().setStatus(HttpServletResponse.SC_OK);
                    }
                });
                get("/users/:id", new Runnable() {
                    @Override
                    public void run() {
                        String id = Env.params("id");
                        Env.req().setAttribute("id", id);
                        Env.res().setStatus(HttpServletResponse.SC_OK);
                    }
                });
                get("/files/*/:name", new Runnable() {
                    @Override
                    public void run() {
                        String name = Env.params("name");
                        Env.req().setAttribute("name", name);
                        Env.res().setStatus(HttpServletResponse.SC_OK);
                    }
                });
                post("/submit", new Runnable() {
                    @Override
                    public void run() {
                        Env.res().setStatus(201);
                    }
                });
                delete("/items/:id", new Runnable() {
                    @Override
                    public void run() {
                        Env.res().setStatus(HttpServletResponse.SC_NO_CONTENT);
                    }
                });
                // Test alias
                get("/alias/:name", "/files/*/:name");
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
    public void testExactMatch() {
        HttpServletRequest req = getRequestMock("GET", "/");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    public void testNamedParam() {
        HttpServletRequest req = getRequestMock("GET", "/users/42");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_OK);
        assertEquals("42", Env.params("id"));
    }

    @Test
    public void testWildcardAndParam() {
        HttpServletRequest req = getRequestMock("GET", "/files/anything/report.pdf");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_OK);
        assertEquals("report.pdf", Env.params("name"));
    }

    @Test
    public void testPost() {
        HttpServletRequest req = getRequestMock("POST", "/submit");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(201);
    }

    @Test
    public void testDelete() {
        HttpServletRequest req = getRequestMock("DELETE", "/items/99");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    @Test
    public void testAlias() {
        HttpServletRequest req = getRequestMock("GET", "/alias/report.pdf");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_OK);
        assertEquals("report.pdf", Env.params("name"));
    }

    @Test
    public void testNotFound() {
        HttpServletRequest req = getRequestMock("GET", "/nonexistent");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testPathLengthMismatch() {
        HttpServletRequest req = getRequestMock("GET", "/users/42/extra");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testWrongMethod() {
        HttpServletRequest req = getRequestMock("PUT", "/submit");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();
        verify(res).setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}
