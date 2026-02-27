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
     * Validates that a redirect location does not contain CR or LF
     * characters, which could enable HTTP response splitting
     * (header injection).
     *
     * @param location the redirect target URL
     * @throws IllegalArgumentException if location is null or contains CR/LF
     */
    public static void validateLocation(String location) {
        if (location == null) {
            throw new IllegalArgumentException(
                    "Location must not be null");
        }
        if (location.indexOf('\r') >= 0 || location.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(
                    "Location must not contain CR or LF characters "
                    + "(possible header injection)");
        }
    }
}
