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

package io.github.ghosthack.turismo.http;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.ghosthack.turismo.Turismo;

/**
 * Embedded HTTP server backed by the JDK's built-in {@link HttpServer}.
 * Routes are defined via the {@link Turismo} static API and dispatched
 * automatically.
 *
 * <pre>{@code
 * import static io.github.ghosthack.turismo.Turismo.*;
 *
 * get("/hello", () -> print("Hello World"));
 * start(8080);
 * }</pre>
 *
 * <p>The server can also be used directly for more control:
 *
 * <pre>{@code
 * Server server = new Server(8080);
 * server.start();
 * // ...
 * server.stop();
 * }</pre>
 *
 * @see Turismo#start(int)
 */
public class Server {

    private final HttpServer server;

    /**
     * Creates a server bound to the given port.
     *
     * @param port the port to listen on (use 0 for a random available port)
     * @throws IOException if the server socket cannot be created
     */
    public Server(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/", this::handle);
    }

    /**
     * Starts the server. This method returns immediately; the server
     * accepts connections on background threads.
     */
    public void start() {
        server.start();
    }

    /**
     * Stops the server immediately, closing all connections.
     */
    public void stop() {
        server.stop(0);
    }

    /**
     * Returns the port the server is listening on. This is useful when
     * the server was created with port 0 to let the OS assign a free port.
     *
     * @return the port number
     */
    public int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) {
        HttpContext ctx = new HttpContext(exchange);
        try {
            Turismo.handle(ctx);
        } catch (Exception e) {
            ctx.resetBuffer();
            ctx.status(500);
            ctx.print("Internal Server Error");
        }
        try {
            ctx.finish();
        } catch (IOException ignored) {
            // Client may have disconnected
        }
    }
}
