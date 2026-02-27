# turismo

A Sinatra-like Java web framework built on the Servlet API.

[![CI](https://github.com/ghosthack/turismo/actions/workflows/ci.yml/badge.svg)](https://github.com/ghosthack/turismo/actions/workflows/ci.yml) [![Javadocs](https://javadoc.io/badge/io.github.ghosthack/turismo.svg)](https://javadoc.io/doc/io.github.ghosthack/turismo) [![Maven Central](https://img.shields.io/maven-central/v/io.github.ghosthack/turismo)](https://central.sonatype.com/artifact/io.github.ghosthack/turismo)

## Maven

```xml
<dependency>
    <groupId>io.github.ghosthack</groupId>
    <artifactId>turismo</artifactId>
    <version>2.0.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.github.ghosthack:turismo:2.0.0'
```

Requires Java 11+.

> **Note:** Versions 1.x were published under `com.ghosthack:turismo`. The groupId changed to
> `io.github.ghosthack` starting with 2.0.0.

## Quick start

```java
import io.github.ghosthack.turismo.action.Action;
import io.github.ghosthack.turismo.routes.RoutesMap;

public class AppRoutes extends RoutesMap {
    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public void run() {
                print("Hello World!");
            }
        });
    }
}
```

## Route types

### RoutesMap — exact match (O(1) lookup)

```java
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
                String id = params("id");
                print("User " + id);
            }
        });

        // Wildcards match any segment
        get("/files/*/download", new Action() {
            @Override
            public void run() {
                print("Downloading file");
            }
        });

        // Route aliases
        get("/u/:id", "/users/:id");
    }
}
```

## HTTP methods

All standard methods are supported: `get`, `post`, `put`, `delete`, `head`, `options`, `trace`, `patch`.

```java
post("/search", new Action() {
    @Override
    public void run() {
        String query = req().getParameter("q");
        print("Search: " + query);
    }
});

delete("/users/:id", new Action() {
    @Override
    public void run() {
        String id = params("id");
        print("Deleted user " + id);
    }
});
```

## Redirects

```java
get("/old-page", new Action() {
    @Override
    public void run() {
        movedPermanently("/new-page"); // 301
    }
});

get("/temp", new Action() {
    @Override
    public void run() {
        redirect("/destination"); // 302 via sendRedirect
    }
});
```

## JSP rendering

```java
get("/render", new Action() {
    @Override
    public void run() {
        req().setAttribute("message", "Hello World!");
        jsp("/WEB-INF/views/render.jsp");
    }
});
```

`render.jsp`:

```jsp
<%= request.getAttribute("message") %>
```

## Custom default route

The default route sends a 404. Override it:

```java
route(new Action() {
    @Override
    public void run() {
        res().setStatus(404);
        print("Nothing here.");
    }
});
```

## Multipart file uploads

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

Or use `MultipartFilter` in `web.xml` to handle parsing automatically:

```xml
<filter>
    <filter-name>multipart</filter-name>
    <filter-class>io.github.ghosthack.turismo.multipart.MultipartFilter</filter-class>
</filter>
<filter-mapping>
    <filter-name>multipart</filter-name>
    <url-pattern>/upload/*</url-pattern>
</filter-mapping>
```

## Deployment

### Embedded Jetty

```java
import io.github.ghosthack.turismo.servlet.Servlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

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
<web-app xmlns="http://java.sun.com/xml/ns/javaee" version="3.1">

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

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
