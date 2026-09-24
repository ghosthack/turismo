package io.github.ghosthack.turismo.util;

import static org.junit.Assert.assertSame;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import io.github.ghosthack.turismo.Routes;
import io.github.ghosthack.turismo.servlet.TestRoutes;

public class ClassForNameTest {

    @Test
    public void testUsesContextClassLoader() throws Exception {
        // Defines its own copy of TestRoutes, as a web app class loader would
        String name = TestRoutes.class.getName();
        ClassLoader webapp = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected synchronized Class<?> loadClass(String n, boolean resolve)
                    throws ClassNotFoundException {
                if (!n.equals(name)) {
                    return super.loadClass(n, resolve);
                }
                Class<?> c = findLoadedClass(n);
                if (c == null) {
                    try (InputStream in = getParent().getResourceAsStream(
                            n.replace('.', '/') + ".class")) {
                        byte[] bytes = in.readAllBytes();
                        c = defineClass(n, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(n, e);
                    }
                }
                return c;
            }
        };
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(webapp);
        try {
            Class<?> loaded = ClassForName.forName(name, Routes.class);
            assertSame(webapp, loaded.getClassLoader());
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test
    public void testFallsBackWithoutContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            assertSame(TestRoutes.class, ClassForName.forName(
                    TestRoutes.class.getName(), Routes.class));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Test(expected = ClassForName.ClassForNameException.class)
    public void testWrongTypeIsWrapped() throws Exception {
        ClassForName.createInstance("java.lang.String", Routes.class);
    }
}
