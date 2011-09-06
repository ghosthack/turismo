package com.ghosthack.turismo;

import com.ghosthack.turismo.servlet.Env;


/**
 * Represents an action to perform 
 */
public interface IAction {

    /**
     * 
     */
    void perform(Env env);

}