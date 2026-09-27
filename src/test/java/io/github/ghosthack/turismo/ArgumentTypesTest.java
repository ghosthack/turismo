package io.github.ghosthack.turismo;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.ghosthack.turismo.TurismoTest.MockContext;
import io.github.ghosthack.turismo.annotation.GET;
import io.github.ghosthack.turismo.annotation.POST;

/** Controller arguments: big numbers, booleans, arrays, repeated values. */
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
