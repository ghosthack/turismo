package io.github.ghosthack.turismo.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.Resolver;
import io.github.ghosthack.turismo.Routes;
import io.github.ghosthack.turismo.action.Action;
import io.github.ghosthack.turismo.action.ActionException;
import io.github.ghosthack.turismo.routes.RoutesList;
import io.github.ghosthack.turismo.routes.RoutesMap;

public class ServletServiceTest {

    private Servlet servlet;
    private ServletContext context;
    private StringWriter out;

    @BeforeEach
    public void setUp() {
        servlet = new Servlet();
        context = mock(ServletContext.class);
        servlet.context = context;
        servlet.routes = new RoutesMap() {
            @Override
            protected void map() {
                get("/hello", new Action() {
                    @Override
                    public void run() {
                        print("Hello " + params("name"));
                    }
                });
                get("/fail", new Action() {
                    @Override
                    public void run() {
                        throw new IllegalStateException("boom");
                    }
                });
                get("/action-fail", new Action() {
                    @Override
                    public void run() {
                        throw new ActionException("wrapped");
                    }
                });
                get("/admin/secret", new Action() {
                    @Override
                    public void run() {
                        print("secret");
                    }
                });
            }
        };
        out = new StringWriter();
    }

    @AfterEach
    public void tearDown() {
        Env.destroy();
    }

    private static HttpServletRequest request(String method, String path) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getPathInfo()).thenReturn(path);
        when(req.getServletPath()).thenReturn("");
        when(req.getContextPath()).thenReturn("");
        when(req.getRequestURI()).thenReturn(path);
        return req;
    }

    private HttpServletResponse response() throws Exception {
        HttpServletResponse res = mock(HttpServletResponse.class);
        when(res.getWriter()).thenReturn(new PrintWriter(out, true));
        return res;
    }

    @Test
    public void testRunsMatchingAction() throws Exception {
        HttpServletRequest req = request("GET", "/hello");
        when(req.getParameter("name")).thenReturn("World");
        HttpServletResponse res = response();

        servlet.service(req, res);

        assertEquals("Hello World", out.toString());
        verify(res, never()).sendError(anyInt());
    }

    @Test
    public void testNoMatchIs404() throws Exception {
        HttpServletResponse res = response();

        servlet.service(request("GET", "/missing"), res);

        verify(res).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testNullActionIs404() throws Exception {
        servlet.routes = new Routes() {
            @Override
            public Resolver getResolver() {
                return new Resolver() {
                    @Override public Runnable resolve() { return null; }
                    @Override public void route(String m, String p, Runnable r) { }
                    @Override public void route(String m, String f, String t) { }
                    @Override public void route(Runnable r) { }
                };
            }
        };
        HttpServletResponse res = response();

        servlet.service(request("GET", "/"), res);

        verify(res).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testExceptionIs500() throws Exception {
        HttpServletResponse res = response();

        servlet.service(request("GET", "/fail"), res);

        verify(res).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertNull(Env.get());
    }

    @Test
    public void testActionExceptionIs500() throws Exception {
        HttpServletResponse res = response();

        servlet.service(request("GET", "/action-fail"), res);

        verify(res).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testExceptionAfterCommitIsRethrown() throws Exception {
        HttpServletResponse res = response();
        when(res.isCommitted()).thenReturn(true);

        ServletException e = assertThrows(ServletException.class,
                () -> servlet.service(request("GET", "/fail"), res));

        assertTrue(e.getCause() instanceof IllegalStateException);
        verify(res, never()).sendError(anyInt());
        assertNull(Env.get());
    }

    @Test
    public void testEnvRemovedAfterRequest() throws Exception {
        servlet.service(request("GET", "/hello"), response());

        assertNull(Env.get());
    }

    @Test
    public void testDefaultsCharsetToUtf8() throws Exception {
        HttpServletRequest req = request("GET", "/hello");

        servlet.service(req, response());

        verify(req).setCharacterEncoding("UTF-8");
    }

    @Test
    public void testKeepsDeclaredCharset() throws Exception {
        HttpServletRequest req = request("GET", "/hello");
        when(req.getCharacterEncoding()).thenReturn("ISO-8859-1");

        servlet.service(req, response());

        verify(req, never()).setCharacterEncoding(anyString());
    }

    @Test
    public void testNestedDispatchRestoresEnv() throws Exception {
        final List<Object> seen = new ArrayList<>();
        servlet.routes = new RoutesMap() {
            @Override
            protected void map() {
                get("/outer", new Action() {
                    @Override
                    public void run() {
                        forward("/inner");
                        // Still the outer request's Env after the forward
                        seen.add(req());
                        seen.add(res());
                    }
                });
                get("/inner", new Action() {
                    @Override
                    public void run() {
                        seen.add(req());
                        print("inner");
                    }
                });
            }
        };
        final HttpServletRequest outerReq = request("GET", "/outer");
        final HttpServletResponse outerRes = response();
        final HttpServletRequest innerReq = request("GET", "/inner");
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(context.getRequestDispatcher("/inner")).thenReturn(dispatcher);
        doAnswer(inv -> {
            // The container calls back into the servlet
            servlet.service(innerReq, (HttpServletResponse) inv.getArgument(1));
            return null;
        }).when(dispatcher).forward(any(ServletRequest.class), any(ServletResponse.class));

        servlet.service(outerReq, outerRes);

        verify(outerRes, never()).sendError(anyInt());
        assertEquals(3, seen.size());
        assertSame(innerReq, seen.get(0));
        assertSame(outerReq, seen.get(1));
        assertSame(outerRes, seen.get(2));
        assertEquals("inner", out.toString());
        assertNull(Env.get());
    }

    @Test
    public void testEncodedSlashDoesNotReachExactRoute() throws Exception {
        HttpServletRequest req = request("GET", "/admin/secret");
        when(req.getServletPath()).thenReturn("/app");
        when(req.getContextPath()).thenReturn("/ctx");
        when(req.getRequestURI()).thenReturn("/ctx/app/admin%2Fsecret");
        HttpServletResponse res = response();

        servlet.service(req, res);

        assertEquals("", out.toString());
        verify(res).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testEncodedSlashStaysInsideParam() throws Exception {
        servlet.routes = new RoutesList() {
            @Override
            protected void map() {
                get("/files/:name", new Action() {
                    @Override
                    public void run() {
                        print("file " + params("name"));
                    }
                });
                get("/admin/secret", new Action() {
                    @Override
                    public void run() {
                        print("secret");
                    }
                });
            }
        };
        HttpServletRequest req = request("GET", "/files/a/b");
        when(req.getServletPath()).thenReturn("/app");
        when(req.getRequestURI()).thenReturn("/app/files/a%2Fb;jsessionid=1");
        servlet.service(req, response());
        assertEquals("file a/b", out.toString());

        out.getBuffer().setLength(0);
        HttpServletRequest admin = request("GET", "/admin/secret");
        when(admin.getRequestURI()).thenReturn("/admin%2fsecret");
        HttpServletResponse res = response();
        servlet.service(admin, res);
        assertEquals("", out.toString());
        verify(res).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

}
