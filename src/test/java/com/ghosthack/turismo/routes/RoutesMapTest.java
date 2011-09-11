package com.ghosthack.turismo.routes;

import static com.ghosthack.turismo.HttpMocks.getRequestMock;
import static com.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import com.ghosthack.turismo.servlet.Env;

public class RoutesMapTest {

    private RoutesMap routes;

    @Before
    public void setUp() {
        routes = new RoutesMap() {
            @Override
            protected void map() {
                // TEST GET
                get("/", new Runnable() {
                    @Override
                    public void run(){
                        Env.res().setStatus(200);
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
                        Env.res().setStatus(404);
                    }
                });
            }
        };
    }

    @Test
    public void testGET1() throws IOException {

        HttpServletRequest req = getRequestMock("GET", "/");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();

        verify(res).setStatus(200);
    }

    @Test
    public void testGET2() throws IOException {

        HttpServletRequest req = getRequestMock("GET", "/foo");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();

        verify(res).setStatus(201);
    }

    @Test
    public void testPUT() throws IOException {

        HttpServletRequest req = getRequestMock("PUT", "/");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();

        verify(res).setStatus(202);
    }

    @Test
    public void testPOST() throws IOException {

        HttpServletRequest req = getRequestMock("POST", "/bar");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();

        verify(res).setStatus(203);
    }

    @Test
    public void testNotFound() throws IOException {

        HttpServletRequest req = getRequestMock("POST", "/everyThingElse");
        HttpServletResponse res = getResponseMock();
        Env.create(req, res, null);
        routes.getResolver().resolve().run();

        verify(res).setStatus(404);
    }
}
