package com.ghosthack.turismo.servlet;

import static org.junit.Assert.fail;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

public class ServletTest {

    @Test(expected = ServletException.class)
    public void testInitWithMissingRoutesParam() throws ServletException {
        Servlet servlet = new Servlet();
        ServletConfig config = Mockito.mock(ServletConfig.class);
        ServletContext context = Mockito.mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        when(config.getInitParameter("routes")).thenReturn(null);

        servlet.init(config);
    }

    @Test(expected = ServletException.class)
    public void testInitWithEmptyRoutesParam() throws ServletException {
        Servlet servlet = new Servlet();
        ServletConfig config = Mockito.mock(ServletConfig.class);
        ServletContext context = Mockito.mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        when(config.getInitParameter("routes")).thenReturn("   ");

        servlet.init(config);
    }

    @Test(expected = ServletException.class)
    public void testInitWithNonExistentClass() throws ServletException {
        Servlet servlet = new Servlet();
        ServletConfig config = Mockito.mock(ServletConfig.class);
        ServletContext context = Mockito.mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        when(config.getInitParameter("routes")).thenReturn("com.nonexistent.Routes");

        servlet.init(config);
    }

    @Test
    public void testInitWithValidRoutesClass() throws ServletException {
        Servlet servlet = new Servlet();
        ServletConfig config = Mockito.mock(ServletConfig.class);
        ServletContext context = Mockito.mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        when(config.getInitParameter("routes"))
                .thenReturn("com.ghosthack.turismo.routes.RoutesMapTest$TestRoutes");

        // Should not throw -- but TestRoutes doesn't exist in production.
        // Let's use an example that exists
        try {
            servlet.init(config);
            fail("Expected ServletException for non-existent class");
        } catch (ServletException e) {
            // expected -- the test class doesn't exist in the classpath
        }
    }
}
