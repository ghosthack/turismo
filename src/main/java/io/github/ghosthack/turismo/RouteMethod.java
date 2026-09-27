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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
 * the Java parameter name (compiled with {@code -parameters}), and is
 * read with {@link Turismo#param(String)} (path, query, then form
 * parameters) and converted to the argument
 * type: {@code String}, a primitive or its wrapper, an enum (by constant
 * name) or {@link UUID}.
 *
 * <p>A value that can't be converted, or a missing value for a
 * primitive argument, is answered with {@code 400 Bad Request}; a missing
 * value for any other type is passed as {@code null}.
 */
final class RouteMethod implements Runnable {

    private final Object instance;
    private final Method method;
    private final Supplier<Object>[] binders;

    @SuppressWarnings("unchecked")
    RouteMethod(Object instance, Method method) {
        this.instance = instance;
        this.method = method;
        Parameter[] parameters = method.getParameters();
        this.binders = new Supplier[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            binders[i] = binder(parameters[i]);
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

    private Supplier<Object> binder(Parameter p) {
        Class<?> type = p.getType();
        if (type == Context.class) {
            return () -> Turismo.context();
        }
        if (type == InputStream.class) {
            return () -> Turismo.body();
        }
        String name = parameterName(p);
        Function<String, Object> converter = converter(type);
        if (converter == null) {
            throw new IllegalArgumentException("Unsupported type "
                    + type.getName() + " for parameter '" + name + "' of "
                    + describe());
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
                // Includes NumberFormatException
                throw new RequestException(400, "Bad Request: "
                        + "invalid value for parameter '" + name + "'");
            }
        };
    }

    private String parameterName(Parameter p) {
        Param param = p.getAnnotation(Param.class);
        if (param != null) {
            if (param.value().isEmpty()) {
                throw new IllegalArgumentException(
                        "@Param name must not be empty in " + describe());
            }
            return param.value();
        }
        if (!p.isNamePresent()) {
            throw new IllegalArgumentException("Cannot bind parameter "
                    + p.getName() + " of " + describe()
                    + ": annotate it with @Param or compile with -parameters");
        }
        return p.getName();
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
        if (type.isEnum()) {
            return s -> Enum.valueOf((Class<? extends Enum>) type, s);
        }
        return null;
    }

    /** Strict boolean parsing: unlike Boolean.valueOf, rejects "yes". */
    private static Boolean parseBoolean(String s) {
        if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
        throw new IllegalArgumentException();
    }
}
