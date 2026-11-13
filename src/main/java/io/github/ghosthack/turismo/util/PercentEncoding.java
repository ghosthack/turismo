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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Percent-encoding helpers for URL paths, shared by the embedded server's
 * routing and the servlet resolvers.
 */
public final class PercentEncoding {

    private PercentEncoding() {
    }

    /**
     * Decodes {@code %XX} escapes as UTF-8. Unlike
     * {@link java.net.URLDecoder}, {@code +} is left alone (it is literal
     * in paths) and malformed escapes are kept as-is.
     *
     * @param s the encoded text
     * @return the decoded text
     */
    public static String decode(String s) {
        if (s.indexOf('%') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            while (i + 2 < s.length() && s.charAt(i) == '%'
                    && hex(s.charAt(i + 1)) >= 0 && hex(s.charAt(i + 2)) >= 0) {
                bytes.write(hex(s.charAt(i + 1)) << 4 | hex(s.charAt(i + 2)));
                i += 3;
            }
            if (bytes.size() > 0) {
                sb.append(bytes.toString(StandardCharsets.UTF_8));
                bytes.reset();
            }
            if (i < s.length()) {
                sb.append(s.charAt(i++));
            }
        }
        return sb.toString();
    }

    /**
     * Returns whether a raw (still encoded) path contains an encoded
     * slash, {@code %2F} or {@code %2f}.
     *
     * @param rawPath the raw path, may be null
     * @return {@code true} if it contains an encoded slash
     */
    public static boolean hasEncodedSlash(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        for (int i = rawPath.indexOf('%'); i >= 0 && i + 2 < rawPath.length();
                i = rawPath.indexOf('%', i + 1)) {
            if (rawPath.charAt(i + 1) == '2'
                    && (rawPath.charAt(i + 2) == 'F'
                        || rawPath.charAt(i + 2) == 'f')) {
                return true;
            }
        }
        return false;
    }

    private static int hex(char c) {
        return Character.digit(c, 16);
    }
}
