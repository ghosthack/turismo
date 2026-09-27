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

package io.github.ghosthack.turismo.util;

import java.lang.reflect.InvocationTargetException;

/**
 * Utility for reflective class loading and instantiation by name.
 */
public class ClassForName {

    private ClassForName() {
    }

    /**
     * Exception thrown when reflective class loading or instantiation fails.
     */
    public static class ClassForNameException extends Exception {

        /**
         * Wraps the given cause.
         *
         * @param e the underlying exception
         */
        public ClassForNameException(Exception e) {
            super(e);
        }

        /**
         * Wraps the given cause, such as a {@link LinkageError} raised
         * while linking or initializing the class.
         *
         * @param e the underlying error or exception
         */
        public ClassForNameException(Throwable e) {
            super(e);
        }

        private static final long serialVersionUID = 1L;

    }

    /**
     * Loads a class by name, verifies it implements the given interface,
     * and creates a new instance via its no-arg constructor. Surrounding
     * whitespace in the name is ignored. The class is not initialized
     * (its static initializers don't run) until it has been checked to
     * be a subtype of {@code interfaceClass}.
     *
     * @param <T>            the expected type
     * @param implClassName  the fully qualified class name
     * @param interfaceClass the interface or superclass the class must extend
     * @return a new instance of the class
     * @throws ClassForNameException if loading, linking, initialization or
     *         instantiation fails, including when the class is not a
     *         subtype of {@code interfaceClass}, is abstract, or its
     *         constructor throws
     */
    public static <T> T createInstance(String implClassName,
            Class<T> interfaceClass) throws ClassForNameException {
        try {
            Class<? extends T> impl = forName(implClassName, interfaceClass);
            return impl.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException | NoSuchMethodException
                | InvocationTargetException | ClassCastException
                | IllegalArgumentException e) {
            throw new ClassForNameException(e);
        } catch (LinkageError e) {
            // ExceptionInInitializerError, NoClassDefFoundError, ...
            throw new ClassForNameException(e);
        }
    }

    /**
     * Loads a class by name and verifies it is a subtype of the given interface.
     * The thread context class loader is tried first, so a web application's
     * classes are found even when turismo is on a container's shared
     * classpath; the class loader that loaded turismo is the fallback.
     * Surrounding whitespace in the name is ignored. The class is loaded
     * without being initialized, so its static initializers don't run
     * unless it is a subtype of {@code interfaceClass} and is later
     * instantiated.
     *
     * @param <T>            the expected type
     * @param implClassName  the fully qualified class name
     * @param interfaceClass the interface or superclass
     * @return the loaded class
     * @throws ClassNotFoundException if the class cannot be found
     * @throws ClassCastException if the class is not a subtype of
     *         {@code interfaceClass}
     */
    public static <T> Class<? extends T> forName(String implClassName,
            Class<T> interfaceClass) throws ClassNotFoundException {
        if (implClassName == null) {
            throw new ClassNotFoundException("Class name is null");
        }
        Class<?> clazz = load(implClassName.trim());
        Class<? extends T> impl = clazz.asSubclass(interfaceClass);
        return impl;
    }

    private static Class<?> load(String className)
            throws ClassNotFoundException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            try {
                return Class.forName(className, false, tccl);
            } catch (ClassNotFoundException e) {
                // fall back to turismo's own class loader
            }
        }
        return Class.forName(className, false, ClassForName.class.getClassLoader());
    }

}
