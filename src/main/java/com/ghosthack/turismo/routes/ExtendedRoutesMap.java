package com.ghosthack.turismo.routes;

import com.ghosthack.turismo.action.Action;

public abstract class ExtendedRoutesMap extends RoutesMap {
    
    protected void get(String path, final String target) {
        resolver.route(GET, path, new Action() {
            @Override public void run() {
                alias(target);
            }
        });
    }

}
