package com.ghosthack.turismo.servlet;

import static com.ghosthack.turismo.util.ClassForName.createInstance;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ghosthack.turismo.IAction;
import com.ghosthack.turismo.Resolver;
import com.ghosthack.turismo.Routes;
import com.ghosthack.turismo.action.ActionException;
import com.ghosthack.turismo.util.ClassForName.ClassForNameException;


/**
 * Action servlet.
 * <p>
 * Resolves a {@link IAction} based on the request, Each route executes an
 * {@link IAction}. On init configures the {@link Routes} {@link Resolver}.
 * 
 * <pre>
 * 	&lt;servlet&gt;
 * 		&lt;servlet-name&gt;app-action-servlet&lt;/servlet-name&gt;
 * 		&lt;servlet-class&gt;action.Servlet&lt;/servlet-class&gt;
 * 		&lt;init-param&gt;
 * 			&lt;param-name&gt;routes&lt;/param-name&gt;
 * 			&lt;param-value&gt;example.Routes&lt;/param-value&gt;
 * 		&lt;/init-param&gt;
 * 	&lt;/servlet&gt;
 * 	&lt;servlet-mapping&gt;
 * 		&lt;servlet-name&gt;app-action-servlet&lt;/servlet-name&gt;
 * 		&lt;url-pattern&gt;/app/*&lt;/url-pattern&gt;
 * 	&lt;/servlet-mapping&gt;
 * </pre>
 */
public class Servlet extends HttpServlet {

    private static final String ROUTES = "routes";
    private static final long serialVersionUID = 1L;
    
    private transient Resolver resolver;
    private transient ServletContext context;

    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        final Env env = new Env(req, res, context);
        try {
            final IAction action = resolver.resolve(env);
            action.perform(env);
        } catch (ActionException e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init();
        context = config.getServletContext();
        final String routesParam = config.getInitParameter(ROUTES);
        try {
            final Routes routes = createInstance(routesParam, Routes.class);
            resolver = routes.getResolver();
        } catch (ClassForNameException e) {
            throw new ServletException(e);
        }
    }

}
