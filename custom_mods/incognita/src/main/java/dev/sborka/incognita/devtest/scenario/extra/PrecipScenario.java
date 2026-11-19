package dev.sborka.incognita.devtest.scenario.extra;

import dev.sborka.incognita.devtest.ClientTestDriver;
import dev.sborka.incognita.weather.clouds.config.CloudConfig;
import dev.sborka.incognita.weather.fog.FogClient;
import dev.sborka.incognita.weather.precip.PrecipConfig;
import dev.sborka.incognita.weather.precip.PrecipitationClient;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;

/**
 * Rain and snow look scenario on the open platform: day, night and fog, a look at the ground for splashes, a roof
 * overhead to check that nothing falls indoors, and vanilla shots of the same views for comparison.
 */
public final class PrecipScenario {
    private PrecipScenario() {}

    public static void addSteps(List<ClientTestDriver.Step> steps, ClientTestDriver driver) {
        Minecraft mc = Minecraft.getInstance();
        steps.add(new ClientTestDriver.Step("settle", 120, () -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.getTutorial().stop();
            mc.options.pauseOnLostFocus = false;
            mc.options.onboardAccessibility = false;
            PrecipConfig.setTestEnabled(true);
            CloudConfig.setTestDisabled(true);
            FogClient.setTestOverride(0.0);
            driver.command("gamerule doDaylightCycle false");
            driver.command("gamerule doWeatherCycle false");
            driver.command("gamemode creative");
        }));
        steps.add(new ClientTestDriver.Step("platform", 80, () -> {
            driver.command("fill ~-14 ~-1 ~-14 ~14 ~-1 ~14 minecraft:stone");
            driver.command("fill ~-14 ~ ~-14 ~14 ~14 ~14 minecraft:air");
            // A few pillars give the rain something to fall past and splash around.
            driver.command("fill ~5 ~ ~5 ~5 ~3 ~5 minecraft:stone_bricks");
            driver.command("fill ~-6 ~ ~4 ~-6 ~2 ~4 minecraft:stone_bricks");
        }));
        steps.add(new ClientTestDriver.Step("hide-hud", 20, () -> {
            mc.options.hideGui = true;
            mc.gui.getChat().clearMessages(true);
        }));
        for (boolean snow : new boolean[] {false, true}) {
            String kind = snow ? "snow" : "rain";
            steps.add(new ClientTestDriver.Step(kind + "-set", 140, () -> {
                PrecipitationClient.setTestSnow(snow);
                driver.command("time set 6000");
                driver.command("weather rain");
            }));
            view(steps, driver, mc, kind + "-day-horizon", 90f, 0f);
            view(steps, driver, mc, kind + "-day-down", 90f, 38f);
            steps.add(new ClientTestDriver.Step(kind + "-night-set", 60, () -> driver.command("time set 18000")));
            view(steps, driver, mc, kind + "-night-horizon", 90f, 0f);
            steps.add(new ClientTestDriver.Step(kind + "-fog-set", 60, () -> {
                driver.command("time set 6000");
                FogClient.setTestOverride(0.5);
            }));
            view(steps, driver, mc, kind + "-fog-horizon", 90f, 0f);
            steps.add(new ClientTestDriver.Step(kind + "-fog-off", 20, () -> FogClient.setTestOverride(0.0)));
            // Vanilla for comparison, same time of day and view.
            steps.add(new ClientTestDriver.Step(kind + "-vanilla-set", 60, () -> PrecipConfig.setTestEnabled(false)));
            view(steps, driver, mc, kind + "-vanilla-horizon", 90f, 0f);
            steps.add(new ClientTestDriver.Step(kind + "-vanilla-off", 20, () -> PrecipConfig.setTestEnabled(true)));
        }
        // Roof over the player: the particles must stop at it and the open edge must still rain.
        steps.add(new ClientTestDriver.Step("roof", 100, () -> {
            PrecipitationClient.setTestSnow(false);
            driver.command("fill ~-3 ~4 ~-3 ~3 ~4 ~3 minecraft:stone");
        }));
        view(steps, driver, mc, "roof-out", 90f, 0f);
        view(steps, driver, mc, "roof-up", 90f, -60f);
        steps.add(new ClientTestDriver.Step("done", 0, () -> {
            PrecipConfig.setTestEnabled(null);
            PrecipitationClient.setTestSnow(null);
            CloudConfig.setTestDisabled(false);
            ClientTestDriver.log("DONE");
            mc.stop();
        }));
    }

    private static void view(List<ClientTestDriver.Step> steps, ClientTestDriver driver, Minecraft mc, String tag,
                             float yaw, float pitch) {
        steps.add(new ClientTestDriver.Step(tag + "-look", 25, () -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        }));
        steps.add(new ClientTestDriver.Step(tag + "-shot", 5, () -> driver.shot(tag)));
    }
}
