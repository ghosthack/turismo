package io.github.ghosthack.turismo.other;

import io.github.ghosthack.turismo.RoutingTest;
import io.github.ghosthack.turismo.Turismo;
import io.github.ghosthack.turismo.annotation.GET;

/**
 * Declares a method with the same signature as a package-private route
 * method of its superclass in another package: that is not an override,
 * so both routes are registered.
 */
public class CrossPackageController extends RoutingTest.PackageBase {

    @GET("/pp/sub")
    void pp() {
        Turismo.print("sub");
    }
}
