turismo -- a sinatra-like Java web framework without sinatra goodness.
======================================================================

[![Build Status](https://secure.travis-ci.org/ghosthack/turismo.png?branch=master)](http://travis-ci.org/ghosthack/turismo) [![Javadoc](https://javadoc-emblem.rhcloud.com/doc/com.ghosthack/turismo/badge.svg)](http://www.javadoc.io/doc/com.ghosthack/turismo)

Quick intro
-----------
```java
public class AppRoutes extends RoutesList {
    protected void map() {
        get("/", new Action() {
            public void run() {
                print("Hello World!");
            }
        });
    }
}
```

Using wildcards and resource identifiers
----------------------------------------

```java
public class AppRoutes extends RoutesList {
    protected void map() {
        get("/wildcard/*/:id", new Action() {
            public void run() {
                String id = params("id");
                print("wildcard id " + id);
            }
        });
        get("/alias/*/:id", "/wildcard/*/:id");
    }
}
```

Testing with standalone jetty
-----------------------------

```java
package com.ghosthack.turismo.example;

import com.ghosthack.turismo.action.*;
import com.ghosthack.turismo.routes.*;

public class AppRoutes extends RoutesList {

    @Override
    protected void map() {
        get("/", new Action() {
            @Override
            public void run() {
                print("Hello World!");
            }
        });
    }

    public static void main(String[] args) throws Exception{
        JettyHelper.server(8080, "/*", AppRoutes.class.getName());
    }

}
```

Getting started, as webapp
--------------------------

Using a webapp descriptor: `web.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<web-app xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd"
  version="2.5">

  <servlet>
    <servlet-name>webapp-servlet</servlet-name>
    <servlet-class>com.ghosthack.turismo.servlet.Servlet</servlet-class>
    <init-param>
      <param-name>routes</param-name>
      <param-value>com.ghosthack.turismo.example.WebAppRoutes</param-value>
    </init-param>
  </servlet>
  <servlet-mapping>
    <servlet-name>webapp-servlet</servlet-name>
    <url-pattern>/*</url-pattern>
  </servlet-mapping>

</web-app>
```

Implementing routes

```java
package com.ghosthack.turismo.example;

import com.ghosthack.turismo.action.*;
import com.ghosthack.turismo.routes.*;

public class WebAppRoutes extends RoutesList {

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

Rendering "templates"
---------------------

Using a jsp: 

```java
get("/render", new Action() {
    public void run() {
        req().setAttribute("message", "Hello Word!");
        jsp("/jsp/render.jsp");
    }
});
```

And the `render.jsp` contains:

```xml
<%=request.getAttribute("message")%>
```

Other mappings
--------------

Methods for GET, POST, PUT, DELETE, HEAD, OPTIONS, TRACE

```java
post("/search", new Action() {
    public void run() {
        String query = req().getParameter("q");
        print("Your search query was: " + query)
    }
});
```

The default route in RoutesMap/RoutesList sends a 404. Rewire with another action:

```java
route(new Action() {
    public void run() {
        try {
            res().sendError(404, "Not Found");
        } catch (IOException e) {
            throw new ActionException(e);
        }
    }
});
```

Multipart
---------

```java
post("/image", new Action() {
    void run() {
        MultipartRequest request = MultipartFilter.wrapAndParse(req());
        String[] meta = request.getParameterValues("image");
        byte[] bytes = (byte[]) request.getAttribute("image");
        LOG.info("type: %s, name: %s, %d bytes", meta[0], meta[1], bytes.length);
    }
});
```


Maven repository
----------------

```xml
<dependency>
    <groupId>com.ghosthack</groupId>
    <artifactId>turismo</artifactId>
    <version>1.1.3</version>
</dependency>
```

