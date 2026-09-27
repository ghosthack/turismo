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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Reads a method's parameter names from the {@code LocalVariableTable} in
 * its class file. That table is written when the class is compiled with
 * debug information ({@code -g}), which Maven, Gradle and IDEs do by
 * default, so names are available even without {@code -parameters}.
 *
 * <p>Only the parts of the class file format needed to reach that table
 * are parsed; anything unexpected yields {@code null} rather than an
 * error, and callers fall back to asking for {@code @Param}.
 */
final class ParameterNames {

    private static final String LOCAL_VARIABLE_TABLE = "LocalVariableTable";
    private static final String CODE = "Code";

    private ParameterNames() {
    }

    /**
     * Returns the parameter names of a method, or {@code null} if its
     * class file can't be found or has no local variable names for it.
     */
    static String[] of(Method method) {
        Class<?> c = method.getDeclaringClass();
        String resource = c.getName().substring(c.getName().lastIndexOf('.') + 1)
                + ".class";
        try (InputStream in = c.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            return read(new DataInputStream(in), method);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static String[] read(DataInputStream in, Method method)
            throws IOException {
        if (in.readInt() != 0xCAFEBABE) {
            return null;
        }
        in.readUnsignedShort(); // minor version
        in.readUnsignedShort(); // major version
        String[] utf8 = readConstantPool(in);

        in.readUnsignedShort(); // access flags
        in.readUnsignedShort(); // this class
        in.readUnsignedShort(); // super class
        skipFully(in, 2L * in.readUnsignedShort()); // interfaces
        int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) {
            skipFully(in, 6); // access flags, name, descriptor
            skipAttributes(in);
        }

        String name = method.getName();
        String descriptor = descriptor(method);
        int methods = in.readUnsignedShort();
        for (int i = 0; i < methods; i++) {
            in.readUnsignedShort(); // access flags
            String n = utf8[in.readUnsignedShort()];
            String d = utf8[in.readUnsignedShort()];
            if (name.equals(n) && descriptor.equals(d)) {
                return readMethodAttributes(in, utf8, method);
            }
            skipAttributes(in);
        }
        return null;
    }

    /** Reads the constant pool, keeping only the UTF-8 entries. */
    private static String[] readConstantPool(DataInputStream in)
            throws IOException {
        int count = in.readUnsignedShort();
        String[] utf8 = new String[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> utf8[i] = in.readUTF(); // Utf8 (modified UTF-8)
                case 7, 8, 16, 19, 20 -> skipFully(in, 2);
                case 15 -> skipFully(in, 3);
                case 3, 4, 9, 10, 11, 12, 17, 18 -> skipFully(in, 4);
                case 5, 6 -> { // Long and Double take two slots
                    skipFully(in, 8);
                    i++;
                }
                default -> throw new IOException("Unknown constant tag " + tag);
            }
        }
        return utf8;
    }

    private static String[] readMethodAttributes(DataInputStream in,
            String[] utf8, Method method) throws IOException {
        String[] names = null;
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            String attribute = utf8[in.readUnsignedShort()];
            long length = in.readInt() & 0xFFFFFFFFL;
            if (names == null && CODE.equals(attribute)) {
                names = readCode(in, utf8, method);
            } else {
                skipFully(in, length);
            }
        }
        return names;
    }

    private static String[] readCode(DataInputStream in, String[] utf8,
            Method method) throws IOException {
        in.readUnsignedShort(); // max stack
        in.readUnsignedShort(); // max locals
        skipFully(in, in.readInt() & 0xFFFFFFFFL); // bytecode
        skipFully(in, 8L * in.readUnsignedShort()); // exception table

        // Local variable slot of each parameter: slot 0 is `this` for
        // instance methods, and long/double take two slots
        Class<?>[] types = method.getParameterTypes();
        int[] slots = new int[types.length];
        int slot = Modifier.isStatic(method.getModifiers()) ? 0 : 1;
        for (int i = 0; i < types.length; i++) {
            slots[i] = slot;
            slot += (types[i] == long.class || types[i] == double.class) ? 2 : 1;
        }

        String[] names = new String[types.length];
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            String attribute = utf8[in.readUnsignedShort()];
            long length = in.readInt() & 0xFFFFFFFFL;
            if (!LOCAL_VARIABLE_TABLE.equals(attribute)) {
                skipFully(in, length);
                continue;
            }
            int entries = in.readUnsignedShort();
            for (int e = 0; e < entries; e++) {
                int startPc = in.readUnsignedShort();
                in.readUnsignedShort(); // length
                String name = utf8[in.readUnsignedShort()];
                in.readUnsignedShort(); // descriptor
                int index = in.readUnsignedShort();
                // Parameters are live from the first instruction; a later
                // local may reuse a parameter's slot under another name
                if (startPc != 0) {
                    continue;
                }
                for (int p = 0; p < slots.length; p++) {
                    if (slots[p] == index && names[p] == null) {
                        names[p] = name;
                    }
                }
            }
        }
        for (String name : names) {
            if (name == null) {
                return null;
            }
        }
        return names;
    }

    private static void skipAttributes(DataInputStream in) throws IOException {
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            in.readUnsignedShort(); // name
            skipFully(in, in.readInt() & 0xFFFFFFFFL);
        }
    }

    private static void skipFully(DataInputStream in, long n)
            throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new IOException("Truncated class file");
                }
                skipped = 1;
            }
            n -= skipped;
        }
    }

    /** The JVM method descriptor, e.g. {@code (ILjava/lang/String;)V}. */
    static String descriptor(Method method) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> type : method.getParameterTypes()) {
            sb.append(type.descriptorString());
        }
        return sb.append(')')
                .append(method.getReturnType().descriptorString())
                .toString();
    }
}
