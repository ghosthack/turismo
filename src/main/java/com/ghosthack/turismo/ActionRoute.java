package com.ghosthack.turismo;

import com.ghosthack.turismo.servlet.Executable;
import com.ghosthack.turismo.servlet.Route;

public class ActionRoute implements Route {

    public ActionRoute(Executable action) {
        this.action = action;
    }

    public Executable getExecutable() {
        return action;
    }

    private final Executable action;

}
