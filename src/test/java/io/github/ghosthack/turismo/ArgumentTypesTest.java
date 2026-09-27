package io.github.ghosthack.turismo;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.TurismoTest.MockContext;
import io.github.ghosthack.turismo.annotation.GET;
import io.github.ghosthack.turismo.annotation.POST;

/**
 * Controller arguments: big numbers, booleans, arrays, collections and
 * repeated values.
 */
public class ArgumentTypesTest {

    enum Size { S, M, L }

    static class Ctl {
        @GET("/big")
        void big(BigInteger i, BigDecimal d) {
            Turismo.print(i + " " + d);
        }

        @GET("/bool")
        void bool(boolean p, Boolean w) {
            Turismo.print(p + " " + w);
        }

        @GET("/arrays")
        void arrays(String[] tag, int[] n, Boolean[] b, BigDecimal[] d,
                Size[] size, long[] missing) {
            Turismo.print(Arrays.toString(tag) + " " + Arrays.toString(n)
                    + " " + Arrays.toString(b) + " " + Arrays.toString(d)
                    + " " + Arrays.toString(size) + " "
                    + Arrays.toString(missing));
        }

        @GET("/ids/:id")
        void ids(int[] id) {
            Turismo.print(Arrays.toString(id));
        }

        @POST("/checkboxes")
        void checkboxes(String[] topic, BigInteger[] n) {
            Turismo.print(Arrays.toString(topic) + " " + Arrays.toString(n));
        }

        @GET("/collections")
        void collections(List<String> tag, Collection<Integer> n,
                Iterable<BigDecimal> d, Set<Size> size,
                List<? extends Long> w, List<Boolean> missing) {
            Turismo.print(tag + " " + n + " " + d + " " + size + " " + w
                    + " " + missing + " " + tag.getClass().getSimpleName()
                    + " " + size.getClass().getSimpleName());
        }

        @GET("/mutable")
        void mutable(List<String> tag) {
            tag.add("added");
            Collections.sort(tag);
            Turismo.print(tag.toString());
        }

        @POST("/topics")
        void topics(Set<String> topic) {
            Turismo.print(topic.toString());
        }
    }

    static class RawListCtl {
        @SuppressWarnings("rawtypes")
        @GET("/x") void x(List tags) { }
    }

    static class UnboundedCtl {
        @GET("/x") void x(List<?> tags) { }
    }

    static class ObjectListCtl {
        @GET("/x") void x(List<Object> tags) { }
    }

    static class SuperBoundCtl {
        @GET("/x") void x(List<? super Integer> tags) { }
    }

    static class TypeVariableCtl {
        @GET("/x") <T> void x(List<T> tags) { }
    }

    static class NestedListCtl {
        @GET("/x") void x(List<List<String>> tags) { }
    }

    static class ArrayElementCtl {
        @GET("/x") void x(List<int[]> tags) { }
    }

    static class ConcreteListCtl {
        @GET("/x") void x(ArrayList<String> tags) { }
    }

    static class NestedArrayCtl {
        @GET("/x")
        void x(int[][] grid) { }
    }

    static class ObjectArrayCtl {
        @GET("/x")
        void x(Object[] things) { }
    }

    @AfterEach
    public void tearDown() {
        Turismo.reset();
    }

    private static MockContext handle(MockContext ctx) {
        Turismo.controller(new Ctl());
        Turismo.handle(ctx);
        return ctx;
    }

    private static MockContext get(String path, String... query) {
        MockContext ctx = new MockContext("GET", path);
        for (int i = 0; i < query.length; i += 2) {
            ctx.repeatedQuery.computeIfAbsent(query[i],
                    k -> new java.util.ArrayList<>()).add(query[i + 1]);
        }
        return handle(ctx);
    }

    @Test
    public void testBigNumbers() {
        MockContext ctx = get("/big", "i", "123456789012345678901234567890",
                "d", "-0.000000000000000000001");
        assertEquals(200, ctx.statusCode);
        assertEquals("123456789012345678901234567890 -1E-21",
                ctx.printed.toString());
    }

    @Test
    public void testBigNumbersInvalidOrOversizedAre400() {
        assertEquals(400, get("/big", "i", "12x").statusCode);
        assertEquals(400, get("/big", "d", "1.2.3").statusCode);
        assertEquals(400, get("/big", "i", "9".repeat(1001)).statusCode);
        assertEquals(200, get("/big", "i", "9".repeat(1000)).statusCode);
        // Short input, astronomically large or small value
        assertEquals(400, get("/big", "d", "1e999999999").statusCode);
        assertEquals(400, get("/big", "d", "1e-999999999").statusCode);
        assertEquals(200, get("/big", "d", "1e1000").statusCode);
    }

    @Test
    public void testMissingBigNumbersAreNull() {
        assertEquals("null null", get("/big").printed.toString());
    }

    @Test
    public void testBooleans() {
        assertEquals("true false",
                get("/bool", "p", "TRUE", "w", "false").printed.toString());
        assertEquals("false null", get("/bool", "p", "false").printed.toString());
        assertEquals(400, get("/bool", "p", "yes").statusCode);
        assertEquals(400, get("/bool", "w", "1").statusCode);
        assertEquals(400, get("/bool").statusCode); // primitive, missing
    }

    @Test
    public void testArraysCollectRepeatedValuesInOrder() {
        MockContext ctx = get("/arrays",
                "tag", "b", "tag", "a", "tag", "b",
                "n", "3", "n", "-1",
                "b", "true", "b", "False",
                "d", "0.1", "d", "2",
                "size", "L", "size", "S");
        assertEquals(200, ctx.statusCode);
        assertEquals("[b, a, b] [3, -1] [true, false] [0.1, 2] [L, S] []",
                ctx.printed.toString());
    }

    @Test
    public void testMissingArraysAreEmpty() {
        assertEquals("[] [] [] [] [] []", get("/arrays").printed.toString());
    }

    @Test
    public void testArrayWithBadElementIs400() {
        MockContext ctx = get("/arrays", "n", "1", "n", "two");
        assertEquals(400, ctx.statusCode);
        assertTrue(ctx.printed.toString().contains("'n'"));
        assertEquals(400, get("/arrays", "size", "XL").statusCode);
    }

    @Test
    public void testPathParamBindsAsSingleElementArray() {
        assertEquals("[7]", get("/ids/7").printed.toString());
    }

    @Test
    public void testFormCheckboxesBindToArray() {
        MockContext ctx = new MockContext("POST", "/checkboxes")
                .form("topic=java&topic=http&n=1&n=18446744073709551616");
        assertEquals("[java, http] [1, 18446744073709551616]",
                handle(ctx).printed.toString());
    }

    @Test
    public void testUnsupportedArrayTypesRejectedAtRegistration() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Turismo.controller(new NestedArrayCtl()));
        assertTrue(e.getMessage().contains("int[][]"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class,
                () -> Turismo.controller(new ObjectArrayCtl()));
        assertTrue(e.getMessage().contains("java.lang.Object[]"), e.getMessage());
    }

    // ---------------------------------------------------------------
    // Collections
    // ---------------------------------------------------------------

    @Test
    public void testCollectionsCollectRepeatedValues() {
        MockContext ctx = get("/collections",
                "tag", "b", "tag", "a",
                "n", "3", "n", "-1",
                "d", "0.5",
                "size", "L", "size", "S", "size", "L",
                "w", "9000000000");
        assertEquals(200, ctx.statusCode);
        // Set keeps request order and drops the repeated L
        assertEquals("[b, a] [3, -1] [0.5] [L, S] [9000000000] []"
                + " ArrayList LinkedHashSet", ctx.printed.toString());
    }

    @Test
    public void testMissingCollectionsAreEmpty() {
        assertEquals("[] [] [] [] [] [] ArrayList LinkedHashSet",
                get("/collections").printed.toString());
    }

    @Test
    public void testCollectionsAreMutable() {
        assertEquals("[a, added, c]",
                get("/mutable", "tag", "c", "tag", "a").printed.toString());
    }

    @Test
    public void testCollectionWithBadElementIs400() {
        MockContext ctx = get("/collections", "n", "1", "n", "x");
        assertEquals(400, ctx.statusCode);
        assertTrue(ctx.printed.toString().contains("'n'"));
        assertEquals(400, get("/collections", "size", "XL").statusCode);
    }

    @Test
    public void testFormCheckboxesBindToSet() {
        MockContext ctx = new MockContext("POST", "/topics")
                .form("topic=java&topic=http&topic=java");
        assertEquals("[java, http]", handle(ctx).printed.toString());
    }

    @Test
    public void testCollectionsWithoutConcreteElementTypeRejected() {
        for (Object ctl : new Object[] {new RawListCtl(), new UnboundedCtl(),
                new ObjectListCtl(), new SuperBoundCtl(), new TypeVariableCtl(),
                new NestedListCtl()}) {
            IllegalArgumentException e = assertThrows(
                    IllegalArgumentException.class,
                    () -> Turismo.controller(ctl), ctl.getClass().getName());
            assertTrue(e.getMessage().contains("type argument"),
                    e.getMessage());
        }
    }

    @Test
    public void testCollectionsOfUnsupportedTypesRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Turismo.controller(new ArrayElementCtl()));
        assertTrue(e.getMessage().contains("java.util.List<int[]>"),
                e.getMessage());
        e = assertThrows(IllegalArgumentException.class,
                () -> Turismo.controller(new ConcreteListCtl()));
        assertTrue(e.getMessage().contains("java.util.ArrayList"),
                e.getMessage());
    }

    // ---------------------------------------------------------------
    // paramValues / queryValues / formValues
    // ---------------------------------------------------------------

    @Test
    public void testParamValuesLookupOrder() {
        Turismo.post("/p/:id", () -> Turismo.print(
                Turismo.paramValues("id") + " " + Turismo.paramValues("tag")
                + " " + Turismo.paramValues("f") + " "
                + Turismo.paramValues("none") + " " + Turismo.param("tag")));
        MockContext ctx = new MockContext("POST", "/p/1")
                .form("id=9&tag=form&f=x&f=y");
        ctx.repeatedQuery.put("tag", List.of("q1", "q2"));
        Turismo.handle(ctx);
        assertEquals("[1] [q1, q2] [x, y] [] q1", ctx.printed.toString());
    }

    @Test
    public void testFormValuesAndFirstValueAccessors() {
        Turismo.post("/f", () -> Turismo.print(Turismo.formValues("a") + " "
                + Turismo.form("a") + " " + Turismo.formFields() + " "
                + Turismo.formValues("zzz")));
        MockContext ctx = new MockContext("POST", "/f").form("a=1&b=2&a=3");
        Turismo.handle(ctx);
        assertEquals("[1, 3] 1 {a=1, b=2} []", ctx.printed.toString());
    }

    @Test
    public void testValuesListsAreUnmodifiable() {
        Turismo.post("/f", () -> {
            assertThrows(UnsupportedOperationException.class,
                    () -> Turismo.formValues("a").add("x"));
            assertThrows(UnsupportedOperationException.class,
                    () -> Turismo.paramValues("a").add("x"));
        });
        Turismo.handle(new MockContext("POST", "/f").form("a=1"));
    }
}
