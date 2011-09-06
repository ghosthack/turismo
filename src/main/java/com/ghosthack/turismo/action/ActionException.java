package com.ghosthack.turismo.action;

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