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

package com.ghosthack.turismo.util;

public class ClassForName {

    public static class ClassForNameException extends Exception {

        public ClassForNameException(Exception e) {
            super(e);
        }

        private static final long serialVersionUID = 1L;

    }

    public static <T> T createInstance(String implClassName,
            Class<T> interfaceClass) throws ClassForNameException {
        Class<? extends T> impl;
        try {
            impl = forName(implClassName, interfaceClass);
            T instance = impl.newInstance();
            return instance;
        } catch (ClassNotFoundException e) {
            throw new ClassForNameException(e);
        } catch (InstantiationException e) {
            throw new ClassForNameException(e);
        } catch (IllegalAccessException e) {
            throw new ClassForNameException(e);
        }
    }

    public static <T> Class<? extends T> forName(String implClassName,
            Class<T> interfaceClass) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(implClassName);
        Class<? extends T> impl = clazz.asSubclass(interfaceClass);
        return impl;
    }

}
