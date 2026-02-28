# turismo

A lightweight Sinatra/Express-style Java web framework.

[![CI](https://github.com/ghosthack/turismo/actions/workflows/ci.yml/badge.svg)](https://github.com/ghosthack/turismo/actions/workflows/ci.yml) [![Javadocs](https://javadoc.io/badge/io.github.ghosthack/turismo.svg)](https://javadoc.io/doc/io.github.ghosthack/turismo) [![Maven Central](https://img.shields.io/maven-central/v/io.github.ghosthack/turismo)](https://central.sonatype.com/artifact/io.github.ghosthack/turismo)

## Maven

```xml
<dependency>
    <groupId>io.github.ghosthack</groupId>
    <artifactId>turismo</artifactId>
    <version>3.2.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.ghosthack:turismo:3.2.0'
```

Requires Java 17+.

> **Note:** Versions 1.x were published under `com.ghosthack:turismo`. The groupId changed to
> `io.github.ghosthack` starting with 2.0.0.

## Quick start

Zero dependencies -- uses the JDK's built-in HTTP server:

```java
import static io.github.ghosthack.turismo.Turismo.*;

public class App {
    public static void main(String[] args) {
        get("/hello", "Hello World!");
        get("/users/:id", () -> print("User ", param("id")));
        post("/users", () -> json(Map.of("created", true)));
        start(8080);
    }
}
```

## Routing

### Exact paths

```java
get("/hello", "Hello!");
post("/submit", () -> print("Created")); // defaults to 201
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

POST routes default to status 201 (Created):

```java
post("/data", () -> print("created"));        // 201
delete("/users/:id", () -> print("Deleted ", param("id")));
```

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
String s = toJson(Map.of("key", "value")); // serialize without writing

// Redirects
redirect("/new-location");       // 302
movedPermanently("/new-url");    // 301
redirect(307, "/temporary");     // custom code

// 404
notFound();
```

## Custom not-found handler

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

Controller routes use the same routing engine as lambda routes and can be
freely mixed. All request/response methods (`param()`, `print()`, `json()`,
etc.) work the same way inside annotated methods.

## Servlet deployment

turismo also supports deployment in any Jakarta EE 10 servlet container
(Tomcat 10.1+, Jetty 12+, etc.) via the `Servlet` class and
`RoutesMap`/`RoutesList` API.

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

## Releasing

1. Set the release version in `pom.xml` (remove `-SNAPSHOT`)
2. Commit, push, and merge via PR
3. CI detects the version change, creates a GitHub release, and deploys to Maven Central
4. Publish the deployment at https://central.sonatype.com/publishing/deployments

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
