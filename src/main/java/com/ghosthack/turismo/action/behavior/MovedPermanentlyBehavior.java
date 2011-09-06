package com.ghosthack.turismo.action.behavior;

import com.ghosthack.turismo.action.Behavior;
import com.ghosthack.turismo.servlet.Env;

public class MovedPermanentlyBehavior implements Behavior {

    @Override
    public void on(Object location) {
        Env.res().setStatus(301);
        Env.res().setHeader("location", String.valueOf(location));
    }

}
