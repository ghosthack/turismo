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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code application/x-www-form-urlencoded} fields of one request,
 * parsed from the body the first time they are asked for. Once the body
 * has been read here, {@link #body()} replays it, so a handler can still
 * read the raw body afterwards. The other way round cannot work: after a
 * handler has taken the raw body stream, asking for the fields of a form
 * body fails with an {@link IllegalStateException} rather than silently
 * returning none.
 *
 * <p>{@link #context()} is the view of the request handed to handlers, so
 * that {@code Turismo.context().body()} goes through {@link #body()} too.
 */
final class Form {

    static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private final Context ctx;
    private final int maxSize;
    private final Context view = new View();
    private Map<String, String> fields;
    private byte[] bytes;
    private boolean rawBodyTaken;

    Form(Context ctx, int maxSize) {
        this.ctx = ctx;
        this.maxSize = maxSize;
    }

    /** Returns a form field, or null if absent or not a form request. */
    String get(String name) {
        return fields().get(name);
    }

    /** Returns all form fields; empty if not a form request. */
    Map<String, String> fields() {
        if (fields == null) {
            fields = parse();
        }
        return fields;
    }

    /** Returns the request body, replayed if the form consumed it. */
    InputStream body() {
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        rawBodyTaken = true;
        return ctx.body();
    }

    /** Returns the request context, with {@code body()} routed through here. */
    Context context() {
        return view;
    }

    private Map<String, String> parse() {
        String type = ctx.header("Content-Type");
        if (type == null) {
            return Collections.emptyMap();
        }
        int semi = type.indexOf(';');
        String mime = (semi < 0 ? type : type.substring(0, semi)).trim();
        if (!CONTENT_TYPE.equalsIgnoreCase(mime)) {
            return Collections.emptyMap();
        }
        if (rawBodyTaken) {
            throw new IllegalStateException("Form fields are unavailable: "
                    + "the request body was already read through body(); "
                    + "read form fields first, then body() replays it");
        }
        Charset charset = charset(semi < 0 ? "" : type.substring(semi + 1));
        bytes = read();
        return Collections.unmodifiableMap(
                decode(new String(bytes, charset), charset));
    }

    private byte[] read() {
        String length = ctx.header("Content-Length");
        if (length != null) {
            try {
                if (Long.parseLong(length.trim()) > maxSize) {
                    throw tooLarge();
                }
            } catch (NumberFormatException e) {
                throw new RequestException(400,
                        "Bad Request: invalid Content-Length");
            }
        }
        byte[] data;
        try (InputStream in = ctx.body()) {
            data = in.readNBytes(maxSize + 1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (data.length > maxSize) {
            throw tooLarge();
        }
        return data;
    }

    private RequestException tooLarge() {
        return new RequestException(413, "Content Too Large");
    }

    /** The charset parameter of the Content-Type, defaulting to UTF-8. */
    private static Charset charset(String parameters) {
        for (String p : parameters.split(";")) {
            int eq = p.indexOf('=');
            if (eq < 0 || !p.substring(0, eq).trim().toLowerCase(Locale.ROOT)
                    .equals("charset")) {
                continue;
            }
            String name = p.substring(eq + 1).trim();
            if (name.length() >= 2 && name.startsWith("\"")
                    && name.endsWith("\"")) {
                name = name.substring(1, name.length() - 1);
            }
            try {
                return Charset.forName(name);
            } catch (IllegalArgumentException e) {
                throw new RequestException(400,
                        "Bad Request: unsupported charset");
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** Delegates to the request context, except for {@link #body()}. */
    private final class View implements Context {
        @Override public String method() { return ctx.method(); }
        @Override public String path() { return ctx.path(); }
        @Override public String rawPath() { return ctx.rawPath(); }
        @Override public String query(String name) { return ctx.query(name); }
        @Override public String header(String name) { return ctx.header(name); }
        @Override public InputStream body() { return Form.this.body(); }
        @Override public void status(int code) { ctx.status(code); }
        @Override public void header(String name, String value) {
            ctx.header(name, value);
        }
        @Override public void print(String text) { ctx.print(text); }
        @Override public OutputStream output() { return ctx.output(); }
        @Override public void reset() { ctx.reset(); }
    }

    /**
     * Decodes {@code name=value&...} pairs, {@code %XX} escapes with the
     * request's charset. A repeated name keeps its first value.
     */
    private static Map<String, String> decode(String body, Charset charset) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (body.isEmpty()) {
            return fields;
        }
        try {
            for (String pair : body.split("&")) {
                if (pair.isEmpty()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                String name = eq < 0 ? pair : pair.substring(0, eq);
                String value = eq < 0 ? "" : pair.substring(eq + 1);
                fields.putIfAbsent(URLDecoder.decode(name, charset),
                        URLDecoder.decode(value, charset));
            }
        } catch (IllegalArgumentException e) {
            throw new RequestException(400,
                    "Bad Request: malformed form body");
        }
        return fields;
    }
}
