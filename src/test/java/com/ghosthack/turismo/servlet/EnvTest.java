package com.ghosthack.turismo.servlet;

import static com.ghosthack.turismo.HttpMocks.getRequestMock;
import static com.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;

public class EnvTest {

    @After
    public void tearDown() {
        Env.destroy();
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
