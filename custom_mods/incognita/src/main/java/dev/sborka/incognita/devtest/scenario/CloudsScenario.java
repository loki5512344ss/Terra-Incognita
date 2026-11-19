package dev.sborka.incognita.devtest.scenario;

import dev.sborka.incognita.devtest.ClientTestDriver;
import dev.sborka.incognita.weather.clouds.config.CloudConfig;
import dev.sborka.incognita.weather.clouds.gl.GlSupport;
import dev.sborka.incognita.weather.clouds.render.CloudsClient;
import dev.sborka.incognita.weather.fog.FogClient;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;

/**
 * Cloud look scenario in its own world ({@code clienttest-clouds}): noon, sunset, night and rain views, a close-up
 * from right under the cumulus band, a view from between the bands, fog 0.35 / 0.7 / 1.0, a time-lapse of the same
 * view at t, t+1200 and t+2400 ticks (with the per band wind offsets logged, proving the bands move in different
 * directions) and the cost of the pass. The {@code cloudslegacy} scenario (or {@code INCOGNITA_CLOUDS_LEGACY=1}) forces
 * the OpenGL 3.3 path for the run.
 */
public final class CloudsScenario {
    private static final String LEGACY_ENV = "INCOGNITA_CLOUDS_LEGACY";
    /** Fixed spot, so every run looks at the same sky. */
    private static final String SPOT = "0 %d 0";
    private static final int VIEW_Y = 170;
    /** Frame limiter value that means "unlimited". */
    private static final int UNLIMITED_FPS = 260;

    private CloudsScenario() {}

    public static void addSteps(List<ClientTestDriver.Step> steps, ClientTestDriver driver) {
        addSteps(steps, driver, "1".equals(System.getenv(LEGACY_ENV)));
    }

    /** The same views with the OpenGL 3.3 path forced when {@code legacy} is true. */
    public static void addSteps(List<ClientTestDriver.Step> steps, ClientTestDriver driver, boolean legacy) {
        Minecraft mc = Minecraft.getInstance();
        String prefix = legacy ? "legacy-" : "";
        steps.add(new ClientTestDriver.Step("settle", 120, () -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.getTutorial().stop();
            mc.options.pauseOnLostFocus = false;
            mc.options.onboardAccessibility = false;
            // The clouds hook only runs when the vanilla clouds option is on (other scenarios may switch it off).
            mc.options.cloudStatus().set(CloudStatus.FANCY);
            CloudConfig.setTestForceLegacy(legacy);
            ClientTestDriver.log("clouds path: " + GlSupport.path() + ", legacy forced: " + legacy);
            driver.command("gamerule doDaylightCycle false");
            driver.command("gamerule doWeatherCycle false");
            driver.command("weather clear");
            driver.command("time set 6000");
        }));
        steps.add(new ClientTestDriver.Step("creative", 40, () -> driver.command("gamemode creative")));
        steps.add(new ClientTestDriver.Step("fly", 80, () -> fly(mc, driver, VIEW_Y)));
        steps.add(new ClientTestDriver.Step("hide-hud", 60, () -> {
            mc.options.hideGui = true;
            mc.gui.getChat().clearMessages(true);
            FogClient.setTestOverride(0.0);
        }));
        addView(steps, driver, mc, prefix, "1-noon", 6000, false);
        addView(steps, driver, mc, prefix, "2-sunset", 12500, false);
        addView(steps, driver, mc, prefix, "3-night", 18000, false);
        addView(steps, driver, mc, prefix, "4-rain", 6000, true);

        // Close-up from right under the cumulus base, and from between the bands looking across the cumulus tops.
        steps.add(new ClientTestDriver.Step("5-close-set", 100, () -> {
            driver.command("weather clear");
            driver.command("time set 6000");
            fly(mc, driver, 222);
            look(mc, 60f, -55f);
        }));
        shot(steps, driver, prefix, "5-close-below");
        steps.add(new ClientTestDriver.Step("5-close-flat", 30, () -> look(mc, 60f, -12f)));
        shot(steps, driver, prefix, "5-close-side");
        steps.add(new ClientTestDriver.Step("5-far-set", 100, () -> {
            fly(mc, driver, 360);
            look(mc, 60f, -6f);
        }));
        shot(steps, driver, prefix, "5-far-above");
        steps.add(new ClientTestDriver.Step("5-back", 100, () -> fly(mc, driver, VIEW_Y)));

        // Time-lapse straight up: screen motion is the horizontal motion of each band.
        steps.add(new ClientTestDriver.Step("6-lapse-set", 100, () -> {
            driver.command("time set 6000");
            driver.command("weather clear");
            look(mc, 0f, -90f);
        }));
        for (int i = 0; i < 3; i++) {
            String tag = "6-lapse-" + i;
            // The view is set again right before every frame, so all three frames show exactly the same view.
            steps.add(new ClientTestDriver.Step(tag + "-aim", 10, () -> look(mc, 0f, -90f)));
            steps.add(new ClientTestDriver.Step(tag, i < 2 ? 1190 : 5, () -> {
                ClientTestDriver.log(tag + " " + CloudsClient.debugLine());
                driver.shot(prefix + tag);
            }));
        }

        for (double density : new double[] {0.35, 0.7, 1.0}) {
            String tag = String.format(Locale.ROOT, "7-fog-%03d", Math.round(density * 100));
            steps.add(new ClientTestDriver.Step(tag, 80, () -> {
                FogClient.setTestOverride(density);
                look(mc, 90f, -25f);
            }));
            shot(steps, driver, prefix, tag);
        }
        steps.add(new ClientTestDriver.Step("fog-off", 40, () -> FogClient.setTestOverride(0.0)));

        // Cost of the cloud pass: GPU time from the timer query and the frame rate with vsync and the frame limiter off,
        // for the half and the full resolution passes and with no clouds, looking at the horizon and straight up.
        steps.add(new ClientTestDriver.Step("8-perf-unlock", 20, () -> {
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(UNLIMITED_FPS);
        }));
        perf(steps, mc, "horizon", -6f);
        perf(steps, mc, "up", -90f);
        steps.add(new ClientTestDriver.Step("done", 0, () -> {
            CloudConfig.setTestForceLegacy(false);
            CloudConfig.setTestHalfResolution(null);
            ClientTestDriver.log("DONE " + CloudsClient.debugLine());
            mc.stop();
        }));
    }

    private static void perf(List<ClientTestDriver.Step> steps, Minecraft mc, String view, float pitch) {
        for (String mode : new String[] {"half", "full", "off"}) {
            steps.add(new ClientTestDriver.Step("8-perf-" + view + "-" + mode, 300, () -> {
                look(mc, 90f, pitch);
                CloudConfig.setTestDisabled("off".equals(mode));
                CloudConfig.setTestHalfResolution("half".equals(mode));
            }));
            steps.add(new ClientTestDriver.Step("8-perf-" + view + "-" + mode + "-log", 5, () ->
                ClientTestDriver.log(String.format(Locale.ROOT, "perf %s %s fps=%d gpu=%.3f ms visible=%d", view, mode,
                    mc.getFps(), "off".equals(mode) ? 0.0 : CloudsClient.gpuMillis(), CloudsClient.visiblePuffs()))));
        }
        steps.add(new ClientTestDriver.Step("8-perf-" + view + "-restore", 5, () -> {
            CloudConfig.setTestDisabled(false);
            CloudConfig.setTestHalfResolution(null);
        }));
    }

    private static void addView(List<ClientTestDriver.Step> steps, ClientTestDriver driver, Minecraft mc,
                                String prefix, String tag, int time, boolean rain) {
        steps.add(new ClientTestDriver.Step(tag + "-set", 100, () -> {
            driver.command("time set " + time);
            driver.command(rain ? "weather rain" : "weather clear");
        }));
        steps.add(new ClientTestDriver.Step(tag + "-up", 30, () -> look(mc, 90f, -40f)));
        shot(steps, driver, prefix, tag + "-up");
        steps.add(new ClientTestDriver.Step(tag + "-horizon", 30, () -> look(mc, 90f, -6f)));
        shot(steps, driver, prefix, tag + "-horizon");
        steps.add(new ClientTestDriver.Step(tag + "-away", 30, () -> look(mc, -90f, -6f)));
        shot(steps, driver, prefix, tag + "-away");
    }

    private static void shot(List<ClientTestDriver.Step> steps, ClientTestDriver driver, String prefix, String tag) {
        steps.add(new ClientTestDriver.Step(tag + "-shot", 5, () -> driver.shot(prefix + tag)));
    }

    private static void fly(Minecraft mc, ClientTestDriver driver, int y) {
        mc.player.getAbilities().mayfly = true;
        mc.player.getAbilities().flying = true;
        mc.player.onUpdateAbilities();
        driver.command("tp @s " + String.format(Locale.ROOT, SPOT, y));
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }
}
