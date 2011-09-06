package com.ghosthack.turismo.action;

import com.ghosthack.turismo.action.behavior.AliasBehavior;
import com.ghosthack.turismo.action.behavior.JspBehavior;
import com.ghosthack.turismo.action.behavior.MovedPermanentlyBehavior;
import com.ghosthack.turismo.action.behavior.NotFoundBehavior;
import com.ghosthack.turismo.action.behavior.RedirectBehavior;
import com.ghosthack.turismo.action.behavior.StringWriterBehavior;

public abstract class Behaviors {

    public static Behavior jsp() {
        return new JspBehavior();
    }
    
    public static Behavior movedPermanently() {
        return new MovedPermanentlyBehavior();
    }

    public static Behavior notFound() {
        return new NotFoundBehavior();
    }
    
    public static Behavior redirect() {
        return new RedirectBehavior();
    }

    public static Behavior string() {
        return new StringWriterBehavior();
    }

    public static Behavior forward() {
        return new AliasBehavior();
    }

    /**
     * Do not instanciate
     */
    private Behaviors(){}

}
