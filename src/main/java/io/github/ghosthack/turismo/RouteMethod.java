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

package io.github.ghosthack.turismo;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.github.ghosthack.turismo.annotation.Param;

/**
 * Adapts an annotated controller method to a {@link Runnable} route
 * action, binding each method argument from the current request.
 *
 * <p>An argument of type {@link Context} gets the request context and
 * one of type {@link InputStream} gets the request body. The body is
 * bound after every other argument, whatever the declared order, so that
 * parameters can still be read from a form body before the stream is
 * handed over (the stream then replays the body). Any other
 * argument is a request parameter, named by {@link Param @Param} or by
 * the Java parameter name, and is
 * read with {@link Turismo#param(String)} (path, query, then form
 * parameters) and converted to the argument type: {@code String}, a
 * primitive or its wrapper, {@link BigInteger}, {@link BigDecimal}, an
 * enum (by constant name) or {@link UUID}. An array of any of these, or
 * a {@code List}, {@code Collection}, {@code Iterable} or {@code Set} of
 * one, collects every value of a repeated parameter
 * ({@link Turismo#paramValues(String)}).
 *
 * <p>A value that can't be converted, or a missing value for a
 * primitive argument, is answered with {@code 400 Bad Request}; a missing
 * value for any other type is passed as {@code null}, and a missing array
 * or collection parameter as an empty one. Big numbers are limited to
 * {@value #MAX_BIG_NUMBER} characters (and a {@code BigDecimal} to that
 * scale magnitude).
 *
 * <p>Java parameter names come from the {@code MethodParameters} attribute
 * ({@code javac -parameters}) or, failing that, from the class file's
 * debug information ({@code javac -g}, the Maven and Gradle default); see
 * {@link ParameterNames}.
 */
final class RouteMethod implements Runnable {

    private final Object instance;
    private final Method method;
    private final Supplier<Object>[] binders;
    /** Indexes of the binders to run: request body streams last. */
    private final int[] bindOrder;
    private String[] debugNames;
    private boolean debugNamesRead;

    @SuppressWarnings("unchecked")
    RouteMethod(Object instance, Method method) {
        this.instance = instance;
        this.method = method;
        Parameter[] parameters = method.getParameters();
        this.binders = new Supplier[parameters.length];
        this.bindOrder = new int[parameters.length];
        int next = 0;
        for (int i = 0; i < parameters.length; i++) {
            binders[i] = binder(parameters[i], i);
            if (parameters[i].getType() != InputStream.class) {
                bindOrder[next++] = i;
            }
        }
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getType() == InputStream.class) {
                bindOrder[next++] = i;
            }
        }
        method.setAccessible(true);
    }

    @Override
    public void run() {
        // Arguments stay in declared order; only the binding order changes
        Object[] args = new Object[binders.length];
        for (int i : bindOrder) {
            args[i] = binders[i].get();
        }
        try {
            method.invoke(instance, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Supplier<Object> binder(Parameter p, int index) {
        Class<?> type = p.getType();
        if (type == Context.class) {
            return () -> Turismo.context();
        }
        if (type == InputStream.class) {
            return () -> Turismo.body();
        }
        String name = parameterName(p, index);
        if (type.isArray()) {
            return arrayBinder(type.getComponentType(), name);
        }
        if (COLLECTION_TYPES.contains(type)) {
            return collectionBinder(p, name);
        }
        Function<String, Object> converter = converter(type);
        if (converter == null) {
            throw unsupported(type, name);
        }
        return () -> {
            String value = Turismo.param(name);
            if (value == null) {
                if (type.isPrimitive()) {
                    throw new RequestException(400,
                            "Bad Request: missing parameter '" + name + "'");
                }
                return null;
            }
            try {
                return converter.apply(value);
            } catch (IllegalArgumentException e) {
                throw invalid(name);
            }
        };
    }

    /**
     * Binds every value of a repeated parameter ({@code ?tag=a&tag=b}) to
     * an array; a missing parameter gives an empty array.
     */
    private Supplier<Object> arrayBinder(Class<?> component, String name) {
        Function<String, Object> converter = converter(component);
        if (converter == null) {
            throw unsupported(component.arrayType(), name);
        }
        return () -> {
            List<String> values = Turismo.paramValues(name);
            Object array = Array.newInstance(component, values.size());
            for (int i = 0; i < values.size(); i++) {
                try {
                    // Array.set unboxes into primitive arrays
                    Array.set(array, i, converter.apply(values.get(i)));
                } catch (IllegalArgumentException e) {
                    throw invalid(name);
                }
            }
            return array;
        };
    }

    /**
     * Collection types an argument can be declared as. {@code Set} gives a
     * {@code LinkedHashSet} (request order, duplicates dropped); the others
     * an {@code ArrayList}.
     */
    private static final Set<Class<?>> COLLECTION_TYPES =
            Set.of(List.class, Collection.class, Iterable.class, Set.class);

    /**
     * Binds every value of a repeated parameter to a collection whose
     * element type comes from the declared type argument
     * ({@code List<Integer>}, {@code Set<? extends Size>}); a missing
     * parameter gives an empty collection. The collection is a fresh,
     * mutable one per request.
     */
    private Supplier<Object> collectionBinder(Parameter p, String name) {
        Type declared = p.getParameterizedType();
        Class<?> element = elementClass(declared);
        if (element == null) {
            throw new IllegalArgumentException("Parameter '" + name + "' of "
                    + describe() + " needs a concrete type argument, such as "
                    + p.getType().getSimpleName() + "<String>; was "
                    + declared.getTypeName());
        }
        Function<String, Object> converter = converter(element);
        if (converter == null) {
            throw unsupported(declared, name);
        }
        boolean set = p.getType() == Set.class;
        return () -> {
            List<String> values = Turismo.paramValues(name);
            Collection<Object> result = set
                    ? new LinkedHashSet<>() : new ArrayList<>(values.size());
            for (String value : values) {
                try {
                    result.add(converter.apply(value));
                } catch (IllegalArgumentException e) {
                    throw invalid(name);
                }
            }
            return result;
        };
    }

    /**
     * The element class of {@code List<X>} or {@code List<? extends X>};
     * null for a raw type, an unbounded or lower-bounded wildcard, or a
     * type variable.
     */
    private static Class<?> elementClass(Type declared) {
        if (!(declared instanceof ParameterizedType pt)) {
            return null;
        }
        Type arg = pt.getActualTypeArguments()[0];
        if (arg instanceof WildcardType w) {
            if (w.getLowerBounds().length > 0) {
                return null;
            }
            arg = w.getUpperBounds()[0];
        }
        return arg instanceof Class<?> c && c != Object.class ? c : null;
    }

    private IllegalArgumentException unsupported(Type type, String name) {
        return new IllegalArgumentException("Unsupported type "
                + type.getTypeName() + " for parameter '" + name + "' of "
                + describe());
    }

    private static RequestException invalid(String name) {
        // Also covers NumberFormatException, a subclass of
        // IllegalArgumentException, from the converters
        return new RequestException(400, "Bad Request: "
                + "invalid value for parameter '" + name + "'");
    }

    private String parameterName(Parameter p, int index) {
        Param param = p.getAnnotation(Param.class);
        if (param != null) {
            if (param.value().isEmpty()) {
                throw new IllegalArgumentException(
                        "@Param name must not be empty in " + describe());
            }
            return param.value();
        }
        if (p.isNamePresent()) {
            return p.getName();
        }
        Method named = namedMethod();
        if (!debugNamesRead) {
            debugNames = named != null ? ParameterNames.of(named) : null;
            debugNamesRead = true;
        }
        if (debugNames == null) {
            if (Modifier.isAbstract(method.getModifiers())) {
                throw new IllegalArgumentException("Cannot bind parameter "
                        + p.getName() + " of abstract method " + describe()
                        + ": an abstract method's class file has no"
                        + " parameter names, and "
                        + (named == null
                            ? "no implementation was found in "
                                + instance.getClass().getName()
                            : "its implementation "
                                + named.getDeclaringClass().getName()
                                + "." + named.getName()
                                + " has no debug information (-g)")
                        + "; annotate it with @Param, or compile with"
                        + " -parameters");
            }
            throw new IllegalArgumentException("Cannot bind parameter "
                    + p.getName() + " of " + describe()
                    + ": annotate it with @Param, or compile with -parameters"
                    + " or with debug information (-g)");
        }
        return debugNames[index];
    }

    /**
     * The method whose class file holds the parameter names: the route
     * method itself or, when it is abstract (it has no code, and so no
     * local variable names), the most-derived concrete implementation in
     * the instance's class; null if there is none.
     */
    private Method namedMethod() {
        if (!Modifier.isAbstract(method.getModifiers())) {
            return method;
        }
        for (Class<?> c = instance.getClass(); c != null;
                c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(method.getName(),
                        method.getParameterTypes());
                if (!Modifier.isAbstract(m.getModifiers())) {
                    return m;
                }
            } catch (NoSuchMethodException e) {
                // not declared here: look further up
            }
        }
        return null;
    }

    private String describe() {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Function<String, Object> converter(Class<?> type) {
        if (type == String.class) return s -> s;
        if (type == int.class || type == Integer.class) return Integer::valueOf;
        if (type == long.class || type == Long.class) return Long::valueOf;
        if (type == double.class || type == Double.class) {
            return RouteMethod::parseDouble;
        }
        if (type == float.class || type == Float.class) {
            return RouteMethod::parseFloat;
        }
        if (type == short.class || type == Short.class) return Short::valueOf;
        if (type == byte.class || type == Byte.class) return Byte::valueOf;
        if (type == boolean.class || type == Boolean.class) {
            return RouteMethod::parseBoolean;
        }
        if (type == char.class || type == Character.class) {
            return s -> {
                if (s.length() != 1) {
                    throw new IllegalArgumentException();
                }
                return s.charAt(0);
            };
        }
        if (type == UUID.class) return UUID::fromString;
        if (type == BigInteger.class) {
            return s -> new BigInteger(checkLength(s));
        }
        if (type == BigDecimal.class) return RouteMethod::parseBigDecimal;
        if (type.isEnum()) {
            return s -> Enum.valueOf((Class<? extends Enum>) type, s);
        }
        return null;
    }

    /**
     * Longest value accepted for a {@code BigInteger} or {@code BigDecimal}
     * argument, and the largest scale magnitude of a {@code BigDecimal}.
     * Parsing and printing numbers far beyond these grows much faster than
     * the input (a 20-character {@code 1e999999999} prints as a billion
     * digits), so a request could otherwise tie up a thread or exhaust
     * memory.
     */
    static final int MAX_BIG_NUMBER = 1000;

    private static String checkLength(String s) {
        if (s.length() > MAX_BIG_NUMBER) {
            throw new IllegalArgumentException("number too long");
        }
        return s;
    }

    private static BigDecimal parseBigDecimal(String s) {
        BigDecimal value = new BigDecimal(checkLength(s));
        if (Math.abs((long) value.scale()) > MAX_BIG_NUMBER) {
            throw new IllegalArgumentException("exponent out of range");
        }
        return value;
    }

    /**
     * A plain decimal number with an optional exponent, such as
     * {@code -1.5}, {@code .5} or {@code 2e10}; unlike what
     * {@link Double#valueOf} accepts, no {@code NaN}, {@code Infinity},
     * hexadecimal ({@code 0x1p3}), type suffix ({@code 1d}) or
     * surrounding whitespace.
     */
    private static final Pattern DECIMAL = Pattern.compile(
            "[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private static Double parseDouble(String s) {
        if (!DECIMAL.matcher(s).matches()) {
            throw new IllegalArgumentException("not a decimal number");
        }
        double d = Double.parseDouble(s);
        if (Double.isInfinite(d)) {
            throw new IllegalArgumentException("out of range");
        }
        return d;
    }

    private static Float parseFloat(String s) {
        if (!DECIMAL.matcher(s).matches()) {
            throw new IllegalArgumentException("not a decimal number");
        }
        float f = Float.parseFloat(s);
        if (Float.isInfinite(f)) {
            throw new IllegalArgumentException("out of range");
        }
        return f;
    }

    /** Strict boolean parsing: unlike Boolean.valueOf, rejects "yes". */
    private static Boolean parseBoolean(String s) {
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        throw new IllegalArgumentException();
    }
}
