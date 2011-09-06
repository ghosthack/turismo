package com.ghosthack.turismo.action;


public abstract class CustomAction extends Action {

    private final Behavior behavior;

    public CustomAction(Behavior behavior) {
        this.behavior = behavior;
    }

    @Override
    public void run() {
        Object result = customRun();
        this.behavior.on(result);
    }

    protected abstract Object customRun();
}
