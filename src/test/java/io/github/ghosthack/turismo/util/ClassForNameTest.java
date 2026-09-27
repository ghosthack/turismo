package io.github.ghosthack.turismo.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.Resolver;
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

    @Test
    public void testWrongTypeIsWrapped() throws Exception {
        assertThrows(ClassForName.ClassForNameException.class, () -> {
            ClassForName.createInstance("java.lang.String", Routes.class);
        });
    }

    @Test
    public void testNameIsTrimmed() throws Exception {
        Routes routes = ClassForName.createInstance(
                "  " + TestRoutes.class.getName() + "\n", Routes.class);
        assertTrue(routes instanceof TestRoutes);
    }

    @Test
    public void testNullNameIsWrapped() {
        assertThrows(ClassForName.ClassForNameException.class,
                () -> ClassForName.createInstance(null, Routes.class));
    }

    @Test
    public void testAbstractClassIsWrapped() {
        ClassForName.ClassForNameException e = assertThrows(
                ClassForName.ClassForNameException.class,
                () -> ClassForName.createInstance(
                        AbstractRoutes.class.getName(), Routes.class));
        assertTrue(e.getCause() instanceof InstantiationException);
    }

    @Test
    public void testThrowingConstructorIsWrapped() {
        ClassForName.ClassForNameException e = assertThrows(
                ClassForName.ClassForNameException.class,
                () -> ClassForName.createInstance(
                        ThrowingRoutes.class.getName(), Routes.class));
        assertTrue(e.getCause() instanceof InvocationTargetException);
    }

    @Test
    public void testFailingStaticInitIsWrapped() {
        ClassForName.ClassForNameException e = assertThrows(
                ClassForName.ClassForNameException.class,
                () -> ClassForName.createInstance(
                        StaticInitRoutes.class.getName(), Routes.class));
        assertTrue(e.getCause() instanceof ExceptionInInitializerError);
    }

    @Test
    public void testWrongTypeIsNotInitialized() {
        assertThrows(ClassForName.ClassForNameException.class,
                () -> ClassForName.createInstance(
                        NotRoutes.class.getName(), Routes.class));
        // Reading a NotRoutes field would initialize it, so it sets a flag here
        assertFalse(MARK.get());
    }

    /** Abstract, so it can't be instantiated. */
    public abstract static class AbstractRoutes implements Routes {
    }

    /** Constructor throws. */
    public static class ThrowingRoutes implements Routes {
        public ThrowingRoutes() {
            throw new IllegalStateException("constructor");
        }

        @Override
        public Resolver getResolver() {
            return null;
        }
    }

    /** Static initializer throws. */
    public static class StaticInitRoutes implements Routes {
        static {
            if (true) {
                throw new IllegalStateException("static init");
            }
        }

        @Override
        public Resolver getResolver() {
            return null;
        }
    }

    /** Not a Routes; records whether its static initializer ran. */
    public static class NotRoutes {
        static {
            MARK.set(true);
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean MARK =
            new java.util.concurrent.atomic.AtomicBoolean();
}
