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
