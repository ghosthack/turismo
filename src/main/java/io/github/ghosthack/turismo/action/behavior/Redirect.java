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

package io.github.ghosthack.turismo.action.behavior;

import java.io.IOException;

import io.github.ghosthack.turismo.action.ActionException;
import io.github.ghosthack.turismo.servlet.Env;
import io.github.ghosthack.turismo.util.Validation;

/**
 * Sends an HTTP redirect response via {@link jakarta.servlet.http.HttpServletResponse#sendRedirect}.
 */
public class Redirect {

    /** Creates a new Redirect instance. */
    public Redirect() {
    }

    /**
     * Sends a redirect to the given location.
     *
     * @param location the redirect URL
     */
    public void redirect(String location) {
        validateLocation(location);
        try {
            Env.res().sendRedirect(location);
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

    /**
     * Validates that the location does not contain CR/LF characters
     * which could enable HTTP response splitting (header injection).
     *
     * @param location the redirect target
     * @throws IllegalArgumentException if location is null or contains CR/LF
     */
    static void validateLocation(String location) {
        Validation.validateLocation(location);
    }

}
