package dev.sborka.incognita.devtest.scenario;

import dev.sborka.incognita.devtest.ClientTestDriver;
import dev.sborka.incognita.weather.clouds.config.CloudConfig;
import dev.sborka.incognita.weather.fog.FogClient;
import dev.sborka.incognita.weather.sky.math.SkyMath;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;

/**
 * Sky scenario: shots toward the sun, away from it, straight up and at the horizon for several times of day, the moon
 * at night, and rain, thunder and fog variants.
 */
public final class SkyScenario {
    private static final int[] TIMES = {0, 1000, 6000, 11500, 12300, 12700, 13200, 13800, 18000, 23000};
    private static final int[] MOON_TIMES = {12900, 18000, 23100};

    private SkyScenario() {}

    public static void addSteps(List<ClientTestDriver.Step> steps, ClientTestDriver driver) {
        Minecraft mc = Minecraft.getInstance();
        steps.add(new ClientTestDriver.Step("settle", 120, () -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.getTutorial().stop();
            mc.options.pauseOnLostFocus = false;
            mc.options.onboardAccessibility = false;
            driver.command("gamerule doDaylightCycle false");
            driver.command("gamerule doWeatherCycle false");
            driver.command("weather clear");
            driver.command("time set 6000");
        }));
        steps.add(new ClientTestDriver.Step("creative", 40, () -> driver.command("gamemode creative")));
        steps.add(new ClientTestDriver.Step("platform", 80, () -> {
            if (driver.isVoidWorld()) {
                // Empty world: a small stone slab for the camera to stand on, no terrain and no clouds around it.
                driver.command("tp @s 0 100 0");
                driver.command("fill -3 99 -3 3 99 3 minecraft:stone");
                CloudConfig.setTestDisabled(true);
                mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
                return;
            }
            driver.command("fill ~-14 ~-1 ~-14 ~14 ~-1 ~14 minecraft:stone");
            driver.command("fill ~-14 ~ ~-14 ~14 ~14 ~14 minecraft:air");
        }));
        steps.add(new ClientTestDriver.Step("hide-hud", 20, () -> {
            mc.options.hideGui = true;
            mc.gui.getChat().clearMessages(true);
            FogClient.setTestOverride(0.0);
        }));

        for (int time : TIMES) {
            String tag = String.format("t%05d", time);
            steps.add(new ClientTestDriver.Step(tag + "-set", 40, () -> driver.command("time set " + time)));
            steps.add(new ClientTestDriver.Step(tag + "-sun", 15, () -> lookAtSun(mc, false)));
            steps.add(new ClientTestDriver.Step(tag + "-sun-shot", 5, () -> driver.shot(tag + "-1-sun")));
            steps.add(new ClientTestDriver.Step(tag + "-away", 15, () -> lookAtSun(mc, true)));
            steps.add(new ClientTestDriver.Step(tag + "-away-shot", 5, () -> driver.shot(tag + "-2-away")));
            steps.add(new ClientTestDriver.Step(tag + "-up", 15, () -> look(mc, 0f, -80f)));
            steps.add(new ClientTestDriver.Step(tag + "-up-shot", 5, () -> driver.shot(tag + "-3-up")));
            steps.add(new ClientTestDriver.Step(tag + "-horizon", 15, () -> look(mc, 180f, 1f)));
            steps.add(new ClientTestDriver.Step(tag + "-horizon-shot", 5, () -> driver.shot(tag + "-4-horizon")));
        }
        for (int time : MOON_TIMES) {
            String tag = String.format("m%05d", time);
            steps.add(new ClientTestDriver.Step(tag + "-set", 40, () -> driver.command("time set " + time)));
            steps.add(new ClientTestDriver.Step(tag + "-moon", 15, () -> lookAtMoon(mc)));
            steps.add(new ClientTestDriver.Step(tag + "-moon-shot", 5, () -> driver.shot(tag + "-moon")));
        }
        // Moon phases at a narrow field of view: full, waning gibbous, last quarter, crescent, new, waxing quarter.
        steps.add(new ClientTestDriver.Step("phase-fov", 5, () -> mc.options.fov().set(30)));
        for (int phase : new int[] {0, 1, 2, 3, 4, 6}) {
            steps.add(new ClientTestDriver.Step("phase" + phase + "-set", 40,
                () -> driver.command("time set " + (phase * 24000 + 17000))));
            steps.add(new ClientTestDriver.Step("phase" + phase + "-look", 15, () -> lookAtMoon(mc)));
            steps.add(new ClientTestDriver.Step("phase" + phase + "-shot", 5,
                () -> driver.shot("p" + phase + "-moon-phase")));
        }
        // A low moon at moonrise, close up: reddened, dimmed, sinking into the haze without a cut.
        steps.add(new ClientTestDriver.Step("phase-low-set", 40, () -> driver.command("time set 12650")));
        steps.add(new ClientTestDriver.Step("phase-low-look", 15, () -> lookAtMoon(mc)));
        steps.add(new ClientTestDriver.Step("phase-low-shot", 5, () -> driver.shot("p0-moon-low")));
        steps.add(new ClientTestDriver.Step("phase-fov-restore", 5, () -> mc.options.fov().set(70)));
        steps.add(new ClientTestDriver.Step("phase-reset", 40, () -> driver.command("time set 18000")));

        // Weather variants at noon and at sunset.
        steps.add(new ClientTestDriver.Step("wx-noon", 40, () -> {
            driver.command("time set 6000");
            driver.command("weather rain");
            look(mc, 0f, -14f);
        }));
        steps.add(new ClientTestDriver.Step("wx-rain-wait", 160, () -> {}));
        steps.add(new ClientTestDriver.Step("wx-rain-shot", 5, () -> driver.shot("w1-rain-noon")));
        steps.add(new ClientTestDriver.Step("wx-thunder", 200, () -> driver.command("weather thunder")));
        steps.add(new ClientTestDriver.Step("wx-thunder-shot", 5, () -> driver.shot("w2-thunder-noon")));
        steps.add(new ClientTestDriver.Step("wx-sunset", 160, () -> {
            driver.command("time set 12300");
            lookAtSun(mc, false);
        }));
        steps.add(new ClientTestDriver.Step("wx-sunset-shot", 5, () -> driver.shot("w3-thunder-sunset")));
        steps.add(new ClientTestDriver.Step("wx-clear", 200, () -> driver.command("weather clear")));

        // Fog variants at day and at sunset.
        steps.add(new ClientTestDriver.Step("fog-day", 40, () -> {
            driver.command("time set 6000");
            look(mc, 0f, -6f);
        }));
        for (double density : new double[] {0.35, 0.7, 1.0}) {
            String tag = String.format("%03d", Math.round(density * 100));
            steps.add(new ClientTestDriver.Step("fog-" + tag, 40, () -> FogClient.setTestOverride(density)));
            steps.add(new ClientTestDriver.Step("fog-" + tag + "-shot", 5, () -> driver.shot("f-" + tag + "-day")));
        }
        // Looking straight up in a fog bank must show the grey dome, not a clear blue sky.
        for (double density : new double[] {0.7, 1.0}) {
            String tag = String.format("%03d", Math.round(density * 100));
            steps.add(new ClientTestDriver.Step("fog-" + tag + "-up", 30, () -> {
                FogClient.setTestOverride(density);
                look(mc, 90f, -88f);
            }));
            steps.add(new ClientTestDriver.Step("fog-" + tag + "-up-shot", 5, () -> driver.shot("f-" + tag + "-up")));
        }
        steps.add(new ClientTestDriver.Step("fog-off", 10, () -> FogClient.setTestOverride(0.0)));
        // The galaxy on its own: clouds hidden, camera aimed at the core of the Milky Way and along the band.
        steps.add(new ClientTestDriver.Step("galaxy-setup", 40, () -> {
            driver.command("time set 18000");
            CloudConfig.setTestDisabled(true);
            look(mc, -59f, -4f);
        }));
        steps.add(new ClientTestDriver.Step("galaxy-core-shot", 5, () -> driver.shot("galaxy-core")));
        steps.add(new ClientTestDriver.Step("galaxy-anti", 20, () -> look(mc, 121f, -8f)));
        steps.add(new ClientTestDriver.Step("galaxy-anti-shot", 5, () -> driver.shot("galaxy-anti")));
        steps.add(new ClientTestDriver.Step("galaxy-up", 20, () -> look(mc, -59f, -55f)));
        steps.add(new ClientTestDriver.Step("galaxy-up-shot", 5, () -> driver.shot("galaxy-up")));
        steps.add(new ClientTestDriver.Step("galaxy-restore", 10, () -> CloudConfig.setTestDisabled(false)));
        steps.add(new ClientTestDriver.Step("fog-sunset", 40, () -> {
            driver.command("time set 12400");
            FogClient.setTestOverride(0.6);
            lookAtSun(mc, false);
        }));
        steps.add(new ClientTestDriver.Step("fog-sunset-shot", 5, () -> driver.shot("f-060-sunset")));
        steps.add(new ClientTestDriver.Step("done", 0, () -> {
            ClientTestDriver.log("DONE");
            mc.stop();
        }));
    }

    /** Looks at the sun (or away from it) with the pitch kept slightly above the horizon so the glow stays in view. */
    private static void lookAtSun(Minecraft mc, boolean away) {
        float[] sun = SkyMath.sunDirection(mc.level.getTimeOfDay(1.0f));
        aim(mc, sun[0], sun[1], away, 3f);
    }

    private static void lookAtMoon(Minecraft mc) {
        float[] sun = SkyMath.sunDirection(mc.level.getTimeOfDay(1.0f));
        aim(mc, -sun[0], -sun[1], false, 3f);
    }

    private static void aim(Minecraft mc, float x, float y, boolean away, float minPitch) {
        // Yaw 0 looks south (+Z), -90 looks east (+X), 90 looks west (-X); the sky path lies in the X/Y plane.
        float yaw = x >= 0 ? -90f : 90f;
        double pitch = -Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, y))));
        if (Math.abs(x) < 0.05f) {
            pitch = y > 0 ? -80.0 : 30.0;
        }
        float finalPitch = (float) Math.max(-80.0, Math.min(-minPitch, pitch));
        if (away) {
            look(mc, yaw + 180f, 8f);
        } else {
            look(mc, yaw, finalPitch);
        }
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }
}
