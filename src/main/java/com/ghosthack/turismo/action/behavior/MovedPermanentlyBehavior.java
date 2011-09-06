package com.ghosthack.turismo.action.behavior;

import com.ghosthack.turismo.action.Behavior;
import com.ghosthack.turismo.servlet.Env;

public class MovedPermanentlyBehavior implements Behavior {

    @Override
    public void behave(Env env, Object location) {
        env.res.setStatus(301);
        env.res.setHeader("location", String.valueOf(location));
    }

}
