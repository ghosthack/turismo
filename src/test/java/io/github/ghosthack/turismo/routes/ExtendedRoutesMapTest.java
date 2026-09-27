package io.github.ghosthack.turismo.routes;

import static io.github.ghosthack.turismo.HttpMocks.getRequestMock;
import static io.github.ghosthack.turismo.HttpMocks.getResponseMock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.action.Action;
import io.github.ghosthack.turismo.servlet.Env;

public class ExtendedRoutesMapTest {

    @AfterEach
    public void tearDown() {
        Env.destroy();
    }

    @Test
    public void testStringTargetForwards() throws Exception {
        ExtendedRoutesMap routes = new ExtendedRoutesMap() {
            @Override
            protected void map() {
                get("/target", new Action() {
                    @Override
                    public void run() {
                        print("target");
                    }
                });
                get("/alias", "/target");
            }
        };
        HttpServletRequest req = getRequestMock("GET", "/alias");
        HttpServletResponse res = getResponseMock();
        ServletContext ctx = mock(ServletContext.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(ctx.getRequestDispatcher("/target")).thenReturn(dispatcher);
        Env.create(req, res, ctx);

        routes.getResolver().resolve().run();

        verify(dispatcher).forward(req, res);
    }

    @Test
    public void testNoMatchIsNotFound() throws Exception {
        ExtendedRoutesMap routes = new ExtendedRoutesMap() {
            @Override
            protected void map() {
                get("/alias", "/target");
            }
        };
        HttpServletResponse res = getResponseMock();
        Env.create(getRequestMock("GET", "/other"), res, null);

        routes.getResolver().resolve().run();

        verify(res).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

}
