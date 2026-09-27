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

package io.github.ghosthack.turismo.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the request parameter bound to a controller method argument.
 * The value is looked up like {@link io.github.ghosthack.turismo.Turismo#param(String)}:
 * path parameters first, then the query string, then the fields of an
 * {@code application/x-www-form-urlencoded} body.
 *
 * <pre>{@code
 * @GET("/items/:id")
 * void getItem(@Param("id") int id) {
 *     print("item: " + id);
 * }
 * }</pre>
 *
 * <p>Without this annotation the Java parameter name is used. It is read
 * from the class file, which records it when the controller is compiled
 * with {@code -parameters} or with debug information ({@code -g}, the
 * Maven and Gradle default).
 *
 * @see io.github.ghosthack.turismo.Turismo#controller(Object)
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {

    /**
     * The request parameter name (e.g. {@code id} for {@code /items/:id}).
     *
     * @return the parameter name
     */
    String value();
}
