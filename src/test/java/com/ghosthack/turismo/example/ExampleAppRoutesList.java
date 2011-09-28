package com.ghosthack.turismo.example;

import com.ghosthack.turismo.action.Action;
import com.ghosthack.turismo.routes.RoutesList;
import com.ghosthack.turismo.servlet.Env;

public class ExampleAppRoutesList extends RoutesList {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public void run() {
                print("Hello World!");
            }
        });
        get("", new Action() {
            @Override
            public void run() {
                print("Hello World!");
            }
        });
        get("/wild/*/card/:id", new Action() {
            @Override
            public void run() {
                String id = params("id");
                String id2 = params("id2");
                print("id " + id + " id2 " + id2);
            }
        });
        get("/redir1", new Action() {
            @Override
            public void run() {
                //301 moved permanently
                movedPermanently("/dest");
            }
        });
        get("/redir2", new Action() {
            @Override
            public void run() {
                redirect("/dest");
            }
        });
        get("/dest", new Action() {
            @Override
            public void run() {
                print("Hello Redirect");
            }
        });
        post("/search", new Action() {
            public void run() {
                String query = Env.req().getParameter("q");
                print("Your search query was: " + query);
            }
        });
        route(new Action() {
            @Override
            public void run() {
                notFound();
            }
        });
    }

    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, "/*", ExampleAppRoutesList.class.getName());
    }

}