package com.ghosthack.turismo.servlet;

public class ActionException extends RuntimeException {

    public ActionException() {
        super();
    }

    public ActionException(String msg) {
        super(msg);
    }

    public ActionException(Throwable cause) {
        super(cause);
    }

    private static final long serialVersionUID = 1L;

}