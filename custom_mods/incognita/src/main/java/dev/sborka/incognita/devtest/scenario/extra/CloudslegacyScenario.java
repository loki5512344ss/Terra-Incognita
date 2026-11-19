package dev.sborka.incognita.devtest.scenario.extra;

import dev.sborka.incognita.devtest.ClientTestDriver;
import dev.sborka.incognita.devtest.scenario.CloudsScenario;

import java.util.List;

/**
 * The clouds scenario on the OpenGL 3.3 path: {@code run-scenario.sh cloudslegacy}. A scenario name instead of an
 * environment variable, because the game is started by the Gradle daemon, which does not see the caller's
 * environment.
 */
public final class CloudslegacyScenario {
    private CloudslegacyScenario() {}

    public static void addSteps(List<ClientTestDriver.Step> steps, ClientTestDriver driver) {
        CloudsScenario.addSteps(steps, driver, true);
    }
}
