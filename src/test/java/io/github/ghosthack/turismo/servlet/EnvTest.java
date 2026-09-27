package io.github.ghosthack.turismo.servlet;

import static io.github.ghosthack.turismo.HttpMocks.getRequestMock;
import static io.github.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class EnvTest {

    @AfterEach
    public void tearDown() {
        Env.destroy();
    }

    @Test
    public void testAccessWithoutCreateThrowsIllegalState() {
        // Env.destroy() was called in tearDown, so no Env exists
        Env.destroy(); // ensure clean state
        try {
            Env.req();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No Env available"));
        }
    }

    @Test
    public void testCreateAndAccess() {
        HttpServletRequest req = getRequestMock("GET", "/");
        HttpServletResponse res = getResponseMock();
        ServletContext ctx = Mockito.mock(ServletContext.class);

        Env.create(req, res, ctx);

        assertSame(req, Env.req());
        assertSame(res, Env.res());
        assertSame(ctx, Env.ctx());
    }

    @Test
    public void testDestroyRemovesContext() {
        HttpServletRequest req = getRequestMock("GET", "/");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        assertNotNull(Env.get());
        Env.destroy();
        assertNull(Env.get());
    }

    @Test
    public void testRestorePutsBackPreviousEnv() {
        HttpServletRequest outer = getRequestMock("GET", "/outer");
        Env.create(outer, getResponseMock(), null);
        Env previous = Env.get();

        Env.create(getRequestMock("GET", "/inner"), getResponseMock(), null);
        Env.restore(previous);

        assertSame(previous, Env.get());
        assertSame(outer, Env.req());
    }

    @Test
    public void testRestoreNullRemovesEnv() {
        Env.create(getRequestMock("GET", "/"), getResponseMock(), null);

        Env.restore(null);

        assertNull(Env.get());
    }

    @Test
    public void testResourceParams() {
        HttpServletRequest req = getRequestMock("GET", "/");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        Map<String, String> params = new HashMap<String, String>();
        params.put("id", "42");
        params.put("name", "test");
        Env.setResourceParams(params);

        assertEquals("42", Env.params("id"));
        assertEquals("test", Env.params("name"));
    }

    @Test
    public void testParamsFallbackToRequestParameter() {
        HttpServletRequest req = getRequestMock("GET", "/");
        when(req.getParameter("q")).thenReturn("search-query");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);

        // "q" is not in resource params, should fall back to request parameter
        assertEquals("search-query", Env.params("q"));
    }

    @Test
    public void testThreadIsolation() throws Exception {
        HttpServletRequest req1 = getRequestMock("GET", "/a");
        HttpServletResponse res1 = getResponseMock();
        Env.create(req1, res1, null);

        final HttpServletRequest[] otherThreadReq = new HttpServletRequest[1];
        final Env[] otherThreadEnv = new Env[1];

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpServletRequest req2 = getRequestMock("POST", "/b");
                HttpServletResponse res2 = getResponseMock();
                Env.create(req2, res2, null);
                otherThreadReq[0] = Env.req();
                otherThreadEnv[0] = Env.get();
                Env.destroy();
            }
        });
        t.start();
        t.join();

        // Main thread's Env should be unaffected
        assertSame(req1, Env.req());
        // Other thread had different request
        assertNotNull(otherThreadReq[0]);
        assertEquals("POST", otherThreadReq[0].getMethod());
    }
}
