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

package io.github.ghosthack.turismo.resolver;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Encoded-slash handling for servlet requests, matching the embedded
 * server's routing: the container decodes {@code %2F} in the path info
 * into {@code /}, which would let {@code /admin%2Fsecret} reach an
 * {@code /admin/secret} route. When the raw request URI has an encoded
 * slash, routes are matched against the raw path's segments, each
 * percent-decoded on its own, so the slash stays inside its segment.
 */
final class EncodedPath {

    private EncodedPath() {
    }

    /**
     * Returns the decoded segments of the request's raw path (relative to
     * the servlet when {@code fromPathInfo}), or {@code null} if the raw
     * path has no encoded slash and the container-decoded path can be
     * used as is.
     *
     * @param req          the request
     * @param fromPathInfo whether the routed path is the path info (as
     *                     opposed to the servlet path)
     * @return the segments, or {@code null}
     */
    static String[] segments(HttpServletRequest req, boolean fromPathInfo) {
        String raw = req.getRequestURI();
        if (!hasEncodedSlash(raw)) {
            return null;
        }
        String contextPath = req.getContextPath();
        if (contextPath != null && raw.startsWith(contextPath)) {
            raw = raw.substring(contextPath.length());
        }
        if (fromPathInfo) {
            // Skip the servlet path, one raw segment per decoded one
            String servletPath = req.getServletPath();
            int pos = 0;
            for (int i = 0; servletPath != null && i < servletPath.length(); i++) {
                if (servletPath.charAt(i) == '/') {
                    pos = raw.indexOf('/', pos + 1);
                    if (pos < 0) {
                        // Can't line up the raw path: match nothing
                        return new String[] { "", "" };
                    }
                }
            }
            raw = raw.substring(pos);
        }
        String[] segments = raw.split("/");
        for (int i = 0; i < segments.length; i++) {
            segments[i] = percentDecode(stripPathParameters(segments[i]));
        }
        return segments;
    }

    /**
     * Returns whether the raw (undecoded) path contains {@code %2F}.
     *
     * @param rawPath the raw path, or {@code null}
     * @return {@code true} if it has an encoded slash
     */
    static boolean hasEncodedSlash(String rawPath) {
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

    /** Drops {@code ;name=value} path parameters, as containers do. */
    private static String stripPathParameters(String segment) {
        int semi = segment.indexOf(';');
        return semi < 0 ? segment : segment.substring(0, semi);
    }

    /**
     * Decodes {@code %XX} escapes as UTF-8. {@code +} is left alone (it is
     * literal in paths) and malformed escapes are kept as-is.
     */
    static String percentDecode(String s) {
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

    private static int hex(char c) {
        return Character.digit(c, 16);
    }

}
