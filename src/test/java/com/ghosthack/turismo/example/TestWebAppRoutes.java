package com.ghosthack.turismo.example;

import java.io.IOException;

import com.ghosthack.turismo.Action;
import com.ghosthack.turismo.RoutesMap;
import com.ghosthack.turismo.servlet.ActionException;
import com.ghosthack.turismo.servlet.Env;
import com.ghosthack.turismo.servlet.Executable;

public class TestWebAppRoutes extends RoutesMap {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public Object perform(Env env) {
                return "Hello World!";
            }
        });
        get("/redir1", new Action() {
            @Override
            public Object perform(Env env) {
                //301 moved permanently
                env.res.setStatus(301);
                env.res.setHeader("Location", "/dest");
                return "Redirect";
            }
        });
        get("/redir2", new Action() {
            @Override
            public Object perform(Env env) {
                try {
                    //302 built in
                    env.res.sendRedirect("/dest");
                } catch (IOException e) {
                }
                return "Redirect";
            }
        });
        get("/dest", new Action() {
            @Override
            public Object perform(Env env) {
                return "Hello Redirect";
            }
        });
        get("/render", new Action() {
            public Object perform(Env env) {
                env.req.setAttribute("message", "Hello Word!");
                return jsp("/jsp/render.jsp");
            }
        });
        post("/search", new Action() {
            public String perform(Env env) {
                String query = env.req.getParameter("q");
                return "Your search query was: " + query;
            }
        });
        notFound(new Executable() {
            @Override
            public void execute(Env env) {
                try {
                    env.res.sendError(404, "Resource not found");
                } catch (IOException e) {
                    throw new ActionException(e);
                }
            }
        });
    }

    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, JettyHelper.webapp());
    }

}