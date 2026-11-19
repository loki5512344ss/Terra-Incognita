package dev.sborka.incognita.devtest.scenario;

import dev.sborka.incognita.devtest.ClientTestDriver;

import dev.sborka.incognita.weather.clouds.gl.GlSupport;
import dev.sborka.incognita.weather.fog.FogClient;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;

/** Weather and light scenario: clouds by day, fog at several densities, colored lamps at night, a held light. */
public final class WeatherScenario {
    private WeatherScenario() {}

    public static void addSteps(List<ClientTestDriver.Step> steps, ClientTestDriver driver) {
        Minecraft mc = Minecraft.getInstance();
        steps.add(new ClientTestDriver.Step("settle", 120, () -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.getTutorial().stop();
            mc.options.pauseOnLostFocus = false;
            mc.options.onboardAccessibility = false;
            ClientTestDriver.log("opengl: " + com.mojang.blaze3d.platform.GlUtil.getRenderer() + " / "
                + com.mojang.blaze3d.platform.GlUtil.getOpenGLVersion() + ", clouds supported: " + GlSupport.isSupported());
            driver.command("gamerule doDaylightCycle false");
            driver.command("gamerule doWeatherCycle false");
            driver.command("weather clear");
            driver.command("time set 6000");
        }));
        // A flat stone platform at the spawn point, open to the sky, so lamps sit on a plain surface.
        steps.add(new ClientTestDriver.Step("platform", 40, () -> driver.command("gamemode creative")));
        steps.add(new ClientTestDriver.Step("platform-fill", 80, () -> {
            driver.command("fill ~-14 ~-1 ~-14 ~14 ~-1 ~14 minecraft:stone");
            driver.command("fill ~-14 ~ ~-14 ~14 ~14 ~14 minecraft:air");
        }));
        steps.add(new ClientTestDriver.Step("hide-hud", 20, () -> {
            mc.options.hideGui = true;
            mc.gui.getChat().clearMessages(true);
        }));
        steps.add(new ClientTestDriver.Step("clouds-up", 60, () -> {
            look(mc, 0f, -35f);
            FogClient.setTestOverride(0.0);
        }));
        steps.add(new ClientTestDriver.Step("clouds-up-shot", 10, () -> driver.shot("w1-clouds-up")));
        steps.add(new ClientTestDriver.Step("clouds-horizon", 40, () -> look(mc, 90f, 2f)));
        steps.add(new ClientTestDriver.Step("clouds-horizon-shot", 10, () -> driver.shot("w2-clouds-horizon")));
        steps.add(new ClientTestDriver.Step("rain-on", 80, () -> driver.command("weather rain")));
        steps.add(new ClientTestDriver.Step("rain-shot", 10, () -> driver.shot("w3-rain")));
        steps.add(new ClientTestDriver.Step("rain-off", 60, () -> driver.command("weather clear")));
        for (double density : new double[] {0.35, 0.7, 1.0}) {
            String tag = String.format("%03d", Math.round(density * 100));
            steps.add(new ClientTestDriver.Step("fog-" + tag, 60, () -> FogClient.setTestOverride(density)));
            steps.add(new ClientTestDriver.Step("fog-" + tag + "-shot", 10, () -> driver.shot("w4-fog-" + tag)));
        }
        steps.add(new ClientTestDriver.Step("fog-off", 40, () -> FogClient.setTestOverride(0.0)));
        // Night and lamps.
        steps.add(new ClientTestDriver.Step("night", 40, () -> {
            driver.command("time set 18000");
            look(mc, 0f, 14f);
        }));
        steps.add(new ClientTestDriver.Step("lamps", 120, () -> {
            driver.command("setblock ~-7 ~ ~5 incognita:lamp_red");
            driver.command("setblock ~-3 ~ ~7 incognita:lamp_green");
            driver.command("setblock ~ ~ ~8 incognita:lamp_blue");
            driver.command("setblock ~3 ~ ~7 incognita:lamp_purple");
            driver.command("setblock ~7 ~ ~5 incognita:lamp_orange");
        }));
        steps.add(new ClientTestDriver.Step("lamps-shot", 10, () -> driver.shot("l1-lamps")));
        steps.add(new ClientTestDriver.Step("lamps-fog", 60, () -> FogClient.setTestOverride(0.6)));
        steps.add(new ClientTestDriver.Step("lamps-fog-shot", 10, () -> driver.shot("l2-lamps-fog")));
        steps.add(new ClientTestDriver.Step("fog-off-2", 30, () -> FogClient.setTestOverride(0.0)));
        steps.add(new ClientTestDriver.Step("held-light", 80, () -> {
            // Face away from the lamps and look down at the ground in front of the hand.
            look(mc, 180f, 55f);
            driver.command("give @s minecraft:soul_torch");
        }));
        steps.add(new ClientTestDriver.Step("held-shot", 10, () -> driver.shot("l3-held-soul-torch")));
        steps.add(new ClientTestDriver.Step("done", 0, () -> {
            ClientTestDriver.log("DONE");
            mc.stop();
        }));
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }
}
