package com.ghosthack.turismo.action.behavior;

import com.ghosthack.turismo.action.Behavior;

public final class JspBehavior implements Behavior {

    @Override
    public void on(Object obj) {
        new AliasBehavior().on(String.valueOf(obj));
    }

}
