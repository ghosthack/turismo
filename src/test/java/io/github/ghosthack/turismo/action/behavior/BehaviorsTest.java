package io.github.ghosthack.turismo.action.behavior;

import static io.github.ghosthack.turismo.HttpMocks.getRequestMock;
import static io.github.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.action.Action;
import io.github.ghosthack.turismo.action.ActionException;
import io.github.ghosthack.turismo.servlet.Env;

public class BehaviorsTest {

    private HttpServletRequest req;
    private HttpServletResponse res;
    private ServletContext ctx;

    @BeforeEach
    public void setUp() {
        req = getRequestMock("GET", "/");
        res = getResponseMock();
        ctx = mock(ServletContext.class);
        Env.create(req, res, ctx);
    }

    @AfterEach
    public void tearDown() {
        Env.destroy();
    }

    @Test
    public void testForward() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(ctx.getRequestDispatcher("/target")).thenReturn(dispatcher);

        new Alias().forward("/target");

        verify(dispatcher).forward(req, res);
    }

    @Test
    public void testForwardWithoutLeadingSlashIsRejected() {
        assertThrows(ActionException.class, () -> new Alias().forward("target"));
        assertThrows(ActionException.class, () -> new Alias().forward(null));
        verify(ctx, never()).getRequestDispatcher(anyString());
    }

    @Test
    public void testForwardWithoutDispatcher() {
        assertThrows(ActionException.class, () -> new Alias().forward("/none"));
    }

    @Test
    public void testForwardWithoutContext() {
        Env.create(req, res, null);
        assertThrows(ActionException.class, () -> new Alias().forward("/target"));
    }

    @Test
    public void testForwardFailureIsWrapped() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(ctx.getRequestDispatcher("/target")).thenReturn(dispatcher);
        ServletException cause = new ServletException("fail");
        doThrow(cause).when(dispatcher).forward(req, res);

        ActionException e = assertThrows(ActionException.class,
                () -> new Alias().forward("/target"));
        assertSame(cause, e.getCause());
    }

    @Test
    public void testActionAliasForwardAndJsp() throws Exception {
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(ctx.getRequestDispatcher(anyString())).thenReturn(dispatcher);
        Action action = new Action() {
            @Override
            public void run() {
                alias("/a");
                forward("/b");
                jsp("/WEB-INF/views/page.jsp");
            }
        };

        action.run();

        verify(ctx).getRequestDispatcher("/a");
        verify(ctx).getRequestDispatcher("/b");
        verify(ctx).getRequestDispatcher("/WEB-INF/views/page.jsp");
        verify(dispatcher, org.mockito.Mockito.times(3)).forward(req, res);
    }

    @Test
    public void testSend301() {
        new MovedPermanently().send301("/new");

        verify(res).setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        verify(res).setHeader("Location", "/new");
    }

    @Test
    public void testSend302() {
        new MovedTemporarily().send302("http://example.com/x");

        verify(res).setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        verify(res).setHeader("Location", "http://example.com/x");
    }

    @Test
    public void testMovedRejectsHeaderInjection() {
        assertThrows(IllegalArgumentException.class,
                () -> new MovedPermanently().send301("/a\r\nSet-Cookie: x"));
        assertThrows(IllegalArgumentException.class,
                () -> new MovedTemporarily().send302("/a\nb"));
        verify(res, never()).setHeader(anyString(), anyString());
    }

    @Test
    public void testSend404() throws Exception {
        new NotFound().send404();

        verify(res).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    @Test
    public void testSend404WrapsIOException() throws Exception {
        doThrow(new IOException("closed")).when(res).sendError(404);

        assertThrows(ActionException.class, () -> new NotFound().send404());
    }

    @Test
    public void testStringPrinter() throws Exception {
        StringWriter out = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(out));

        new StringPrinter().print("hello");

        assertEquals("hello", out.toString());
    }

    @Test
    public void testStringPrinterWrapsIOException() throws Exception {
        when(res.getWriter()).thenThrow(new IOException("closed"));

        assertThrows(ActionException.class, () -> new StringPrinter().print("x"));
    }

}
