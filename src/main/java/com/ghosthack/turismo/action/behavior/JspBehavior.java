package com.ghosthack.turismo.action.behavior;

import com.ghosthack.turismo.action.Behavior;
import com.ghosthack.turismo.servlet.Env;

public final class JspBehavior implements Behavior {

    @Override
    public void behave(Env env, Object obj) {
        new AliasBehavior().behave(env, String.valueOf(obj));
    }

}
