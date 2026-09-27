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
 * Shared validation utilities used across both the static API and
 * the servlet API.
 */
public final class Validation {

    private Validation() {
    }

    /**
     * Validates that a redirect location contains no control characters
     * (below {@code 0x20}, or {@code 0x7F}). CR and LF would enable HTTP
     * response splitting (header injection); browsers drop others, such
     * as TAB, in ways that can change where the URL points.
     *
     * <p>This does not restrict where the redirect goes; use
     * {@link #isLocalPath(String)} for targets taken from the request.
     *
     * @param location the redirect target URL
     * @throws IllegalArgumentException if location is null or contains
     *         a control character
     */
    public static void validateLocation(String location) {
        if (location == null) {
            throw new IllegalArgumentException(
                    "Location must not be null");
        }
        if (hasControlChar(location)) {
            throw new IllegalArgumentException(
                    "Location must not contain control characters "
                    + "(possible header injection)");
        }
    }

    /**
     * Returns whether a redirect target is a path on the same site: it
     * starts with a single {@code /}, is not protocol-relative
     * ({@code //host}) or its backslash variant ({@code /\host}, which
     * browsers treat the same way), and contains no control characters
     * (browsers drop a TAB, turning {@code /<TAB>/host} into
     * {@code //host}). A scheme ({@code https://host}) is rejected since
     * the target must start with {@code /}.
     *
     * @param location the redirect target
     * @return {@code true} if location is a local path
     */
    public static boolean isLocalPath(String location) {
        if (location == null || location.isEmpty()
                || location.charAt(0) != '/' || hasControlChar(location)) {
            return false;
        }
        if (location.length() > 1) {
            char second = location.charAt(1);
            return second != '/' && second != '\\';
        }
        return true;
    }

    private static boolean hasControlChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
