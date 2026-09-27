# turismo

A lightweight Sinatra/Express-style Java web framework.

[![CI](https://github.com/ghosthack/turismo/actions/workflows/ci.yml/badge.svg)](https://github.com/ghosthack/turismo/actions/workflows/ci.yml) [![Javadocs](https://javadoc.io/badge/io.github.ghosthack/turismo.svg)](https://javadoc.io/doc/io.github.ghosthack/turismo) [![Maven Central](https://img.shields.io/maven-central/v/io.github.ghosthack/turismo)](https://central.sonatype.com/artifact/io.github.ghosthack/turismo)

## Maven

```xml
<dependency>
    <groupId>io.github.ghosthack</groupId>
    <artifactId>turismo</artifactId>
    <version>5.0.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.ghosthack:turismo:5.0.0'
```

Requires Java 21+. (3.x supports Java 17.) See [Upgrading to 5.0](#upgrading-to-50) for breaking changes.

> **Note:** Versions 1.x were published under `com.ghosthack:turismo`. The groupId changed to
> `io.github.ghosthack` starting with 2.0.0.

## Quick start

Zero dependencies -- uses the JDK's built-in HTTP server:

```java
import static io.github.ghosthack.turismo.Turismo.*;

public class Main {
    public static void main(String[] args) {
        get("/hello", "Hello World!");
        get("/users/:id", () -> print("User ", param("id")));
        post("/users", () -> json(Map.of("created", true)));
        start(8080);
    }
}
```

For a runnable starter project, including a servlet deployment example, see
[turismo-bootstrap](https://github.com/ghosthack/turismo-bootstrap).

## Routing

### Exact paths

```java
get("/hello", "Hello!");
post("/submit", () -> print("Submitted"));
```

### Named parameters

```java
get("/users/:id", () -> {
    String id = param("id");
    print("User " + id);
});

get("/users/:userId/posts/:postId", () -> {
    print(param("userId") + "/" + param("postId"));
});
```

Parameter values are percent-decoded, and an encoded slash stays inside its
segment: `/files/a%2Fb` matches `/files/:name` with `name` = `a/b`. For the
same reason `/admin%2Fsecret` does not match an exact `/admin/secret` route.

Paths must start with `/`.

### Wildcards

```java
get("/files/*/download", () -> print("Downloading"));
```

### Query parameters

```java
get("/search", () -> {
    String q = param("q"); // from ?q=turismo
    print("Search: " + q);
});
```

## HTTP methods

All standard methods: `get`, `post`, `put`, `delete`, `patch`, `head`, `options`.

HEAD requests without a `head` route are served by the matching GET route,
with the body discarded. A request whose path matches a route registered only
for other methods gets `405 Method Not Allowed` with an `Allow` header.

Every route responds `200 OK` unless the handler sets a status, POST
included:

```java
post("/users", () -> {
    status(201);                               // Created
    json(Map.of("id", 42));
});
delete("/users/:id", () -> print("Deleted ", param("id")));
```

> **Upgrading from 4.x:** POST routes used to default to `201`. Add
> `status(201)` to handlers that relied on it. See
> [Upgrading to 5.0](#upgrading-to-50).

## Response helpers

```java
// Set status code
status(201);

// Set headers
header("X-Custom", "value");
type("application/json");

// Write body
print("Hello World");
print("Hello ", name, "!");  // varargs — avoids concatenation

// JSON response (built-in serializer, no dependencies)
json(Map.of("ok", true, "count", 42));
json(List.of("a", "b", "c"));
// Also: records (as objects), enums (by name), Character, all array types.
// NaN and Infinity are written as null, as in JavaScript
String s = toJson(Map.of("key", "value")); // serialize without writing

// Redirects
redirect("/new-location");       // 302
movedPermanently("/new-url");    // 301
redirect(307, "/temporary");     // custom code

// 404
notFound();
```

## Custom not-found handler

Runs when no route matches the path (wrong-method requests get a 405 instead):

```java
notFound(() -> {
    status(404);
    type("application/json");
    print("{\"error\":\"not found\"}");
});
```

## Request access

```java
get("/echo", () -> {
    String method = method();            // HTTP method
    String path = path();                // request path
    String auth = header("Authorization"); // request header
    InputStream body = body();           // request body
});
```

## Controller mode

Routes can also be defined as annotated methods on a controller class:

```java
import static io.github.ghosthack.turismo.Turismo.*;
import io.github.ghosthack.turismo.annotation.*;

public class UserController {

    @GET("/hello")
    void hello() {
        print("Hello World!");
    }

    @GET("/users/:id")
    void getUser() {
        print("User ", param("id"));
    }

    @POST("/users")
    void createUser() {
        json(Map.of("created", true));
    }

    @DELETE("/users/:id")
    void deleteUser() {
        print("Deleted ", param("id"));
    }
}
```

Register the controller and start the server:

```java
controller(new UserController());
start(8080);
```

Route methods take no parameters (read them with `param()` and friends);
`controller()` rejects any that do. Annotated methods inherited from a
superclass are registered too, and an annotated override in a subclass
replaces the superclass's route.

Controller routes use the same routing engine as lambda routes and can be
freely mixed. All request/response methods (`param()`, `print()`, `json()`,
etc.) work the same way inside annotated methods.

## Multiple apps

The static `get()`/`start()`/... methods register routes on one shared default
app. Create `App` instances for independent route sets, for example two
servers in one JVM, or a fresh app per test instead of calling `reset()`:

```java
import static io.github.ghosthack.turismo.Turismo.*;

App api = new App();
api.get("/users/:id", () -> json(Map.of("id", param("id"))));
api.start(8080);

App admin = new App();
admin.get("/health", "ok");
admin.start(9090);
```

`App` has the same registration and server methods (`get`, `post`, ...,
`route`, `controller`, `notFound`, `start`, `stop`, `port`, `handle`,
`reset`). Handlers use the usual static helpers (`param()`, `print()`,
`json()`, ...), which work for whichever app is serving the request.
`Turismo.app()` returns the default app.

## Concurrency

The embedded server handles each request on its own virtual thread, so
handlers can block (database calls, outbound HTTP, `Thread.sleep`) without
holding up other requests.

## Stopping and errors

```java
stop();                       // immediate: requests in progress are interrupted
stop(Duration.ofSeconds(10)); // graceful: in-flight requests get up to 10s
```

An exception thrown by a handler results in a `500 Internal Server Error` and
is logged through `System.Logger` (by default `java.util.logging`).

## Servlet deployment

turismo also supports deployment in any Jakarta EE 10 servlet container
(Tomcat 10.1+, Jetty 12+, etc.) via the `Servlet` class and
`RoutesMap`/`RoutesList` API. As with the embedded server, HEAD requests are
served by GET routes and wrong-method requests get `405` with an `Allow`
header.

### RoutesMap — exact match (O(1) lookup)

```java
import io.github.ghosthack.turismo.action.Action;
import io.github.ghosthack.turismo.routes.RoutesMap;

public class AppRoutes extends RoutesMap {
    @Override
    protected void map() {
        get("/hello", new Action() {
            @Override
            public void run() {
                print("Hello!");
            }
        });
    }
}
```

### RoutesList — wildcards and named parameters

```java
import io.github.ghosthack.turismo.routes.RoutesList;

public class AppRoutes extends RoutesList {
    @Override
    protected void map() {
        get("/users/:id", new Action() {
            @Override
            public void run() {
                print("User " + params("id"));
            }
        });

        // Route aliases
        get("/u/:id", "/users/:id");
    }
}
```

### Embedded Jetty

```java
import io.github.ghosthack.turismo.servlet.Servlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;

public class Main {
    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/");
        ServletHolder holder = new ServletHolder(new Servlet());
        holder.setInitParameter("routes", "com.example.AppRoutes");
        ctx.addServlet(holder, "/*");
        server.setHandler(ctx);
        server.start();
        server.join();
    }
}
```

### web.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0">

  <servlet>
    <servlet-name>app</servlet-name>
    <servlet-class>io.github.ghosthack.turismo.servlet.Servlet</servlet-class>
    <init-param>
      <param-name>routes</param-name>
      <param-value>com.example.AppRoutes</param-value>
    </init-param>
  </servlet>
  <servlet-mapping>
    <servlet-name>app</servlet-name>
    <url-pattern>/*</url-pattern>
  </servlet-mapping>

</web-app>
```

### JSP rendering

```java
get("/render", new Action() {
    @Override
    public void run() {
        req().setAttribute("message", "Hello World!");
        jsp("/WEB-INF/views/render.jsp");
    }
});
```

### Multipart file uploads

```java
import io.github.ghosthack.turismo.multipart.MultipartRequest;

post("/upload", new Action() {
    @Override
    public void run() {
        try {
            MultipartRequest multipart = MultipartRequest.wrapAndParse(req());
            String[] meta = multipart.getParameterValues("image");
            String contentType = meta[0];
            String fileName = meta[1];
            byte[] bytes = (byte[]) multipart.getAttribute("image");
            print("Uploaded " + fileName + " (" + bytes.length + " bytes)");
        } catch (Exception e) {
            throw new ActionException(e);
        }
    }
});
```

Uploads are limited to 10 MB by default (`MultipartParser.setMaxContentSize`);
memory is used as the body arrives, not reserved from the declared
`Content-Length`, and chunked uploads are accepted. `wrapAndParse` throws
`ContentTooLargeException` for bodies over the limit and `ParseException` for
malformed ones; `MultipartFilter` answers those with `413` and `400`. Text is
decoded with the request's charset, defaulting to UTF-8. A file part without
a `Content-Type` is reported as `application/octet-stream`.

## Upgrading to 5.0

5.0 changes these defaults and behaviors from 4.x:

- **POST status**: `post()` and `@POST` routes respond `200` unless the
  handler sets a status. Add `status(201)` where you relied on the old
  default.
- **Servlet routes answer 405**: with `RoutesMap`/`RoutesList`, a request
  whose path only matches routes for other methods gets
  `405 Method Not Allowed` with an `Allow` header, instead of 404 or the
  default route. HEAD requests are served by GET routes.
- **Encoded slashes**: `/admin%2Fsecret` no longer matches an exact
  `/admin/secret` route (it could be used to get past path-based access
  rules in a proxy).
- **Route validation**: `route()`, `get()`, ... reject a null method,
  path or action, and paths that don't start with `/`; `notFound(null)` is
  rejected too.
- **Multipart**:
  - Text defaults to UTF-8 instead of ISO-8859-1 when the request has no
    charset.
  - `MultipartFilter` answers malformed bodies with `400` and oversized ones
    with `413` instead of throwing `ServletException`.
  - `wrapAndParse` throws `ContentTooLargeException` (a `ParseException`)
    for oversized bodies, and `ParseException` for a missing boundary or
    unsupported charset.
  - Requests without `Content-Length` are accepted.

New in 5.0: `App` instances ([Multiple apps](#multiple-apps)), graceful
`stop(Duration)`, logging of handler errors, and JSON support for records,
enums, `Character` and all array types.

## Releasing

1. Set the release version in `pom.xml` (remove `-SNAPSHOT`) and merge it to `master` via PR
2. Tag the merged commit on `master` and push the tag:
   ```sh
   git tag v5.0.0
   git push origin v5.0.0
   ```
3. The Release workflow checks that the tag matches the `pom.xml` version and is on
   `master`, deploys to Maven Central, and creates the GitHub release
4. Publish the deployment at https://central.sonatype.com/publishing/deployments

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
