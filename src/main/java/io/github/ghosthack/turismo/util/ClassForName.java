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

        private static final long serialVersionUID = 1L;

    }

    /**
     * Loads a class by name, verifies it implements the given interface,
     * and creates a new instance via its no-arg constructor.
     *
     * @param <T>            the expected type
     * @param implClassName  the fully qualified class name
     * @param interfaceClass the interface or superclass the class must extend
     * @return a new instance of the class
     * @throws ClassForNameException if loading or instantiation fails
     */
    public static <T> T createInstance(String implClassName,
            Class<T> interfaceClass) throws ClassForNameException {
        Class<? extends T> impl;
        try {
            impl = forName(implClassName, interfaceClass);
            T instance = impl.getDeclaredConstructor().newInstance();
            return instance;
        } catch (ClassNotFoundException e) {
            throw new ClassForNameException(e);
        } catch (InstantiationException e) {
            throw new ClassForNameException(e);
        } catch (IllegalAccessException e) {
            throw new ClassForNameException(e);
        } catch (NoSuchMethodException e) {
            throw new ClassForNameException(e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new ClassForNameException(e);
        } catch (ClassCastException e) {
            throw new ClassForNameException(e);
        }
    }

    /**
     * Loads a class by name and verifies it is a subtype of the given interface.
     * The thread context class loader is tried first, so a web application's
     * classes are found even when turismo is on a container's shared
     * classpath; the class loader that loaded turismo is the fallback.
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
        Class<?> clazz = load(implClassName);
        Class<? extends T> impl = clazz.asSubclass(interfaceClass);
        return impl;
    }

    private static Class<?> load(String className)
            throws ClassNotFoundException {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        if (tccl != null) {
            try {
                return Class.forName(className, true, tccl);
            } catch (ClassNotFoundException e) {
                // fall back to turismo's own class loader
            }
        }
        return Class.forName(className);
    }

}
