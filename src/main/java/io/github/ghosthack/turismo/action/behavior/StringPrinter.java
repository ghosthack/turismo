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
import java.util.Locale;

import jakarta.servlet.http.HttpServletResponse;

import io.github.ghosthack.turismo.action.ActionException;
import io.github.ghosthack.turismo.servlet.Env;

/**
 * Writes a string directly to the HTTP response output.
 */
public final class StringPrinter {

    /** Content type of a response whose action set none. */
    public static final String DEFAULT_CONTENT_TYPE =
            "text/plain; charset=UTF-8";

    /** Creates a new StringPrinter. */
    public StringPrinter() {
    }

    /**
     * Writes the given string to the response writer. If the action has
     * not set a content type, the response is sent as
     * {@value #DEFAULT_CONTENT_TYPE}; call
     * {@code res().setContentType("text/html")} first to print HTML. A
     * content type set without a charset is written as UTF-8 rather than
     * the servlet default, ISO-8859-1. Only this method does so: responses
     * written through {@code res()} directly, and forwards, are untouched.
     *
     * @param string the string to write
     */
    public void print(String string) {
        try {
            HttpServletResponse res = Env.res();
            String type = res.getContentType();
            if (type == null) {
                // Without a type browsers sniff the body, which turns
                // text that echoes request data into HTML
                res.setContentType(DEFAULT_CONTENT_TYPE);
            } else if (!type.toLowerCase(Locale.ROOT).contains("charset=")) {
                // The servlet default is ISO-8859-1, which can't write
                // most text. No effect once the writer has been taken
                res.setCharacterEncoding("UTF-8");
            }
            res.getWriter().write(string);
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }

}
