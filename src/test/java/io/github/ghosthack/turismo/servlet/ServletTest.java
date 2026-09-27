package io.github.ghosthack.turismo.servlet;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServletTest {

    @Test
    public void testInitWithMissingRoutesParam() throws ServletException {
        assertThrows(ServletException.class, () -> {
            Servlet servlet = new Servlet();
            ServletConfig config = Mockito.mock(ServletConfig.class);
            ServletContext context = Mockito.mock(ServletContext.class);
            when(config.getServletContext()).thenReturn(context);
            when(config.getInitParameter("routes")).thenReturn(null);

            servlet.init(config);
        });
    }

    @Test
    public void testInitWithEmptyRoutesParam() throws ServletException {
        assertThrows(ServletException.class, () -> {
            Servlet servlet = new Servlet();
            ServletConfig config = Mockito.mock(ServletConfig.class);
            ServletContext context = Mockito.mock(ServletContext.class);
            when(config.getServletContext()).thenReturn(context);
            when(config.getInitParameter("routes")).thenReturn("   ");

            servlet.init(config);
        });
    }

    @Test
    public void testInitWithNonExistentClass() throws ServletException {
        assertThrows(ServletException.class, () -> {
            Servlet servlet = new Servlet();
            ServletConfig config = Mockito.mock(ServletConfig.class);
            ServletContext context = Mockito.mock(ServletContext.class);
            when(config.getServletContext()).thenReturn(context);
            when(config.getInitParameter("routes")).thenReturn("com.nonexistent.Routes");

            servlet.init(config);
        });
    }

    @Test
    public void testInitWithValidRoutesClass() throws ServletException {
        Servlet servlet = new Servlet();
        ServletConfig config = Mockito.mock(ServletConfig.class);
        ServletContext context = Mockito.mock(ServletContext.class);
        when(config.getServletContext()).thenReturn(context);
        when(config.getInitParameter("routes"))
                .thenReturn("io.github.ghosthack.turismo.servlet.TestRoutes");

        servlet.init(config);
        assertNotNull(servlet.routes);
    }

    @Test
    public void testInitFailsWhenMapThrows() throws ServletException {
        assertThrows(ServletException.class, () -> {
            Servlet servlet = new Servlet();
            ServletConfig config = Mockito.mock(ServletConfig.class);
            ServletContext context = Mockito.mock(ServletContext.class);
            when(config.getServletContext()).thenReturn(context);
            when(config.getInitParameter("routes")).thenReturn(
                    "io.github.ghosthack.turismo.servlet.ServletTest$BrokenRoutes");

            servlet.init(config);
        });
    }

    @Test
    public void testInitWithClassThatIsNotRoutes() throws ServletException {
        assertThrows(ServletException.class, () -> {
            Servlet servlet = new Servlet();
            ServletConfig config = Mockito.mock(ServletConfig.class);
            ServletContext context = Mockito.mock(ServletContext.class);
            when(config.getServletContext()).thenReturn(context);
            when(config.getInitParameter("routes")).thenReturn("java.lang.String");

            servlet.init(config);
        });
    }

    /** Routes whose map() fails, loaded by name above. */
    public static class BrokenRoutes extends io.github.ghosthack.turismo.routes.RoutesMap {
        @Override
        protected void map() {
            throw new IllegalStateException("broken map");
        }
    }
}
