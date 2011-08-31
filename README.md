turismo -- a sinatra-like Java web framework without sinatra goodness.
======================================================================

Quick intro
-----------

    public class AppRoutes extends RoutesMap {
        protected void map() {
            get("/", new Action() {
                public Object perform(Env env) {
                    return "Hello World!";
                }
            });
        }
    }


Testing with standalone jetty
-----------------------------

    package com.ghosthack.turismo.example;
    
    import com.ghosthack.turismo.*;
    import com.ghosthack.turismo.servlet.*;
    
    public class AppRoutes extends RoutesMap {
    
        @Override
        protected void map() {
            get("/", new Action() {
                @Override
                public Object perform(Env env) {
                    return "Hello World!";
                }
            });
        }
    
        public static void main(String[] args) throws Exception{
            JettyHelper.server(8080, "/*", AppRoutes.class.getName());
        }
    
    }

Getting started, as webapp
--------------------------

Using a webapp descriptor: web.xml

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

Implementing routes

    package com.ghosthack.turismo.example;
    
    import com.ghosthack.turismo.*;
    import com.ghosthack.turismo.servlet.*;
    
    public class WebAppRoutes extends RoutesMap {
    
        @Override
        protected void map() {
            get("/", new Action() {
                @Override
                public Object perform(Env env) {
                    return "Hello World!";
                }
            });
        }
    
    }

Maven repository
----------------

    <dependency>
        <groupId>com.ghosthack</groupId>
        <artifactId>turismo</artifactId>
        <version>0.1.0</version>
    </dependency>

