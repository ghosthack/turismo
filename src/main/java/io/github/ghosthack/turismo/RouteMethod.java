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
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.ghosthack.turismo.annotation.Param;

/**
 * Adapts an annotated controller method to a {@link Runnable} route
 * action, binding each method argument from the current request.
 *
 * <p>An argument of type {@link Context} gets the request context and
 * one of type {@link InputStream} gets the request body. Any other
 * argument is a request parameter, named by {@link Param @Param} or by
 * the Java parameter name, and is
 * read with {@link Turismo#param(String)} (path, query, then form
 * parameters) and converted to the argument type: {@code String}, a
 * primitive or its wrapper, {@link BigInteger}, {@link BigDecimal}, an
 * enum (by constant name) or {@link UUID}. An array of any of these
 * collects every value of a repeated parameter
 * ({@link Turismo#paramValues(String)}).
 *
 * <p>A value that can't be converted, or a missing value for a
 * primitive argument, is answered with {@code 400 Bad Request}; a missing
 * value for any other type is passed as {@code null}, and a missing array
 * parameter as an empty array. Big numbers are limited to
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
    private String[] debugNames;
    private boolean debugNamesRead;

    @SuppressWarnings("unchecked")
    RouteMethod(Object instance, Method method) {
        this.instance = instance;
        this.method = method;
        Parameter[] parameters = method.getParameters();
        this.binders = new Supplier[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            binders[i] = binder(parameters[i], i);
        }
        method.setAccessible(true);
    }

    @Override
    public void run() {
        Object[] args = new Object[binders.length];
        for (int i = 0; i < binders.length; i++) {
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

    private IllegalArgumentException unsupported(Class<?> type, String name) {
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
        if (!debugNamesRead) {
            debugNames = ParameterNames.of(method);
            debugNamesRead = true;
        }
        if (debugNames == null) {
            throw new IllegalArgumentException("Cannot bind parameter "
                    + p.getName() + " of " + describe()
                    + ": annotate it with @Param, or compile with -parameters"
                    + " or with debug information (-g)");
        }
        return debugNames[index];
    }

    private String describe() {
        return method.getDeclaringClass().getName() + "." + method.getName();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Function<String, Object> converter(Class<?> type) {
        if (type == String.class) return s -> s;
        if (type == int.class || type == Integer.class) return Integer::valueOf;
        if (type == long.class || type == Long.class) return Long::valueOf;
        if (type == double.class || type == Double.class) return Double::valueOf;
        if (type == float.class || type == Float.class) return Float::valueOf;
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

    /** Strict boolean parsing: unlike Boolean.valueOf, rejects "yes". */
    private static Boolean parseBoolean(String s) {
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        throw new IllegalArgumentException();
    }
}
