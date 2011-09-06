package com.ghosthack.turismo;

import static com.ghosthack.turismo.HttpMocks.getRequestMock;
import static com.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.routes.RoutesMap;
import com.ghosthack.turismo.servlet.Env;

public class RoutesMapTest {

    private RoutesMap routes;

    @Before
    public void setUp() {
        routes = new RoutesMap() {
            @Override
            protected void map() {
                // TEST GET
                get("/", new IAction() {
                    @Override
                    public void perform(Env env) throws ActionException {
                        env.res.setStatus(200);
                    }
                });
                get("/foo", new IAction() {
                    @Override
                    public void perform(Env env) throws ActionException {
                        env.res.setStatus(201);
                    }
                });
                put("/", new IAction() {
                    @Override
                    public void perform(Env env) throws ActionException {
                        env.res.setStatus(202);
                    }
                });
                post("/bar", new IAction() {
                    @Override
                    public void perform(Env env) throws ActionException {
                        env.res.setStatus(203);
                    }
                });
                notFound(new IAction() {
                    @Override
                    public void perform(Env env) {
                        env.res.setStatus(404);
                    }
                });
            }
        };
    }

    @Test
    public void testGET1() throws IOException {

        HttpServletRequest req = getRequestMock("GET", "/");
        HttpServletResponse res = getResponseMock();
        Env env = new Env(req, res, null);
        routes.execute(env);

        verify(res).setStatus(200);
    }

    @Test
    public void testGET2() throws IOException {

        HttpServletRequest req = getRequestMock("GET", "/foo");
        HttpServletResponse res = getResponseMock();
        Env env = new Env(req, res, null);
        routes.execute(env);

        verify(res).setStatus(201);
    }

    @Test
    public void testPUT() throws IOException {

        HttpServletRequest req = getRequestMock("PUT", "/");
        HttpServletResponse res = getResponseMock();
        Env env = new Env(req, res, null);
        routes.execute(env);

        verify(res).setStatus(202);
    }

    @Test
    public void testPOST() throws IOException {

        HttpServletRequest req = getRequestMock("POST", "/bar");
        HttpServletResponse res = getResponseMock();
        Env env = new Env(req, res, null);
        routes.execute(env);

        verify(res).setStatus(203);
    }

    @Test
    public void testNotFound() throws IOException {

        HttpServletRequest req = getRequestMock("POST", "/everyThingElse");
        HttpServletResponse res = getResponseMock();
        Env env = new Env(req, res, null);
        routes.execute(env);

        verify(res).setStatus(404);
    }
}
