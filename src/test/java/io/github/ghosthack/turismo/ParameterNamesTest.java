package io.github.ghosthack.turismo;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ghosthack.turismo.TurismoTest.MockContext;

/**
 * Binding controller arguments by Java parameter name when the controller
 * was not compiled with {@code -parameters}. The project itself compiles
 * with {@code -parameters}, so the controller here is compiled at test
 * time with the flags under test.
 */
public class ParameterNamesTest {

    private static final String SOURCE = """
            package sample;

            import io.github.ghosthack.turismo.Turismo;
            import io.github.ghosthack.turismo.annotation.GET;

            public class Ctl {
                @GET("/sum/:a/:b")
                public void sum(long a, int b) {
                    long unrelated = a * 2; // reuses no parameter slot
                    Turismo.print("sum=" + (a + b) + " " + unrelated);
                }

                @GET("/mix/:ratio")
                public void mix(double ratio, String label, boolean on) {
                    Turismo.print(ratio + " " + label + " " + on);
                }

                @GET("/static/:name")
                public static void hello(String name) {
                    Turismo.print("hello " + name);
                }
            }
            """;

    @TempDir
    Path dir;

    @AfterEach
    public void tearDown() {
        Turismo.reset();
    }

    private Class<?> compile(String... options) throws Exception {
        Path src = dir.resolve("src/sample/Ctl.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, SOURCE);
        Path out = dir.resolve("out");
        Files.createDirectories(out);

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<String> args = new ArrayList<>(List.of(options));
        args.addAll(List.of("-classpath", System.getProperty("java.class.path"),
                "-d", out.toString(), src.toString()));
        assertEquals(0, javac.run(null, null, null, args.toArray(new String[0])));

        URLClassLoader loader = new URLClassLoader(
                new URL[] {out.toUri().toURL()}, getClass().getClassLoader());
        return loader.loadClass("sample.Ctl");
    }

    private static Method method(Class<?> c, String name) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new AssertionError(name);
    }

    private static String get(String path) {
        MockContext ctx = new MockContext("GET", path);
        Turismo.handle(ctx);
        return ctx.statusCode + " " + ctx.printed;
    }

    @Test
    public void testNamesFromDebugInfoWithoutParametersFlag() throws Exception {
        Class<?> c = compile("-g");
        Method sum = method(c, "sum");
        assertFalse(sum.getParameters()[0].isNamePresent(),
                "precondition: compiled without -parameters");

        assertArrayEquals(new String[] {"a", "b"}, ParameterNames.of(sum));
        assertArrayEquals(new String[] {"ratio", "label", "on"},
                ParameterNames.of(method(c, "mix")));
        assertArrayEquals(new String[] {"name"},
                ParameterNames.of(method(c, "hello")));
    }

    @Test
    public void testControllerBindsByNameFromDebugInfo() throws Exception {
        Class<?> c = compile("-g");
        Turismo.controller(c.getDeclaredConstructor().newInstance());

        assertEquals("200 sum=10 14", get("/sum/7/3"));
        MockContext ctx = new MockContext("GET", "/mix/0.5");
        ctx.queryParams.put("label", "x");
        ctx.queryParams.put("on", "true");
        Turismo.handle(ctx);
        assertEquals("0.5 x true", ctx.printed.toString());
        assertEquals("200 hello ana", get("/static/ana"));
    }

    @Test
    public void testWithoutNamesRegistrationFailsWithHint() throws Exception {
        Class<?> c = compile("-g:none");
        assertNull(ParameterNames.of(method(c, "sum")));
        Object controller = c.getDeclaredConstructor().newInstance();
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> Turismo.controller(controller));
        assertTrue(e.getMessage().contains("@Param"), e.getMessage());
        assertTrue(e.getMessage().contains("-g"), e.getMessage());
    }

    @Test
    public void testParametersFlagStillWorksWithoutDebugInfo() throws Exception {
        Class<?> c = compile("-g:none", "-parameters");
        Turismo.controller(c.getDeclaredConstructor().newInstance());
        assertEquals("200 sum=10 14", get("/sum/7/3"));
    }

    @Test
    public void testDescriptor() throws Exception {
        Method m = ParameterNamesTest.class.getDeclaredMethod(
                "sample", long.class, String[].class, int.class);
        assertEquals("(J[Ljava/lang/String;I)Ljava/util/List;",
                ParameterNames.descriptor(m));
    }

    @SuppressWarnings("unused")
    private static List<String> sample(long a, String[] b, int c) {
        return null;
    }

    @Test
    public void testMissingClassFileYieldsNull() throws IOException {
        // Lambdas and other generated classes have no class file resource
        Runnable r = () -> { };
        for (Method m : r.getClass().getDeclaredMethods()) {
            assertNull(ParameterNames.of(m));
        }
    }
}
