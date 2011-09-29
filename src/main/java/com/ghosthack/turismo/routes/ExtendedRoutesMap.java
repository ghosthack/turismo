package com.ghosthack.turismo.routes;

import com.ghosthack.turismo.action.Action;

public abstract class ExtendedRoutesMap extends RoutesMap {
    
    protected void route(String path, final String target) {
        resolver.route(null, path, new Action() {
            @Override public void run() {
                alias(target);
            }
        });
    }

}
