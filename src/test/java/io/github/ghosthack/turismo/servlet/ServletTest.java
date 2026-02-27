package io.github.ghosthack.turismo.servlet;

import static org.junit.Assert.assertNotNull;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

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
                .thenReturn("io.github.ghosthack.turismo.example.AppRoutes");

        servlet.init(config);
        assertNotNull(servlet.routes);
    }
}
