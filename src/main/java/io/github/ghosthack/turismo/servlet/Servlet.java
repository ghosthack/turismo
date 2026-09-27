/*
 * Copyright (c) 2011 Adrian Fernandez
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.github.ghosthack.turismo.servlet;

import static io.github.ghosthack.turismo.util.ClassForName.createInstance;

import java.io.IOException;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.ghosthack.turismo.Resolver;
import io.github.ghosthack.turismo.Routes;
import io.github.ghosthack.turismo.util.ClassForName.ClassForNameException;


/**
 * Action servlet.
 * <p>
 * Resolves an action based on the request, Each route executes an
 * action. On init configures the {@link Routes} {@link Resolver}.
 * 
 * <pre>
 * 	&lt;servlet&gt;
 * 		&lt;servlet-name&gt;app-action-servlet&lt;/servlet-name&gt;
 * 		&lt;servlet-class&gt;io.github.ghosthack.turismo.servlet.Servlet&lt;/servlet-class&gt;
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

    /** Creates a new Servlet instance. */
    public Servlet() {
    }

    private static final String ROUTES = "routes";
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_CHARSET = "UTF-8";
    private static final System.Logger LOG =
            System.getLogger(Servlet.class.getName());

    /** The configured routes instance. */
    protected transient Routes routes;
    /** The servlet context obtained during initialization. */
    protected transient ServletContext context;

    /**
     * Resolves and runs the action for the request. A request without a
     * declared charset has its parameters decoded as UTF-8, like the
     * embedded server. No matching action gets {@code 404}; an exception
     * thrown by the action is logged and answered with {@code 500} (or
     * rethrown as a {@link ServletException} if the response is already
     * committed), so container error pages never show its stack trace.
     * A request forwarded back into this servlet (see
     * {@link io.github.ghosthack.turismo.action.Action#forward}) runs with
     * its own {@link Env}, and the forwarding action's Env is restored
     * afterwards.
     */
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        if (req.getCharacterEncoding() == null) {
            req.setCharacterEncoding(DEFAULT_CHARSET);
        }
        final Env previous = Env.get();
        Env.create(req, res, context);
        try {
            final Runnable action;
            try {
                action = routes.getResolver().resolve();
                if (action == null) {
                    res.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                action.run();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "Unhandled error in "
                        + req.getMethod() + " " + req.getRequestURI(), e);
                if (res.isCommitted()) {
                    throw new ServletException(e);
                }
                res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } finally {
            Env.restore(previous);
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        context = config.getServletContext();
        final String routesParam = config.getInitParameter(ROUTES);
        if (routesParam == null || routesParam.trim().isEmpty()) {
            throw new ServletException(
                    "Missing required init-param 'routes'. "
                    + "Specify the fully qualified class name of your Routes implementation.");
        }
        try {
            routes = createInstance(routesParam.trim(), Routes.class);
        } catch (ClassForNameException e) {
            throw new ServletException(e);
        }
        try {
            // Register routes now so a broken map() fails deployment
            // instead of the first request
            routes.getResolver();
        } catch (RuntimeException e) {
            throw new ServletException(
                    "Failed to initialize routes from " + routesParam, e);
        }
    }

}
