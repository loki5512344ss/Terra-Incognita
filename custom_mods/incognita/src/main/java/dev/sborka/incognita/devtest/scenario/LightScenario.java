package dev.sborka.incognita.devtest.scenario;

import dev.sborka.incognita.devtest.ClientTestDriver;
import dev.sborka.incognita.lighting.client.LightingClient;
import dev.sborka.incognita.lighting.field.ColorLightField;
import dev.sborka.incognita.weather.fog.FogClient;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;

/**
 * Colored light scenario: far lamps seen through fog at several densities, the five lamp pools up close, and held
 * item light (soul torch, torch, lamp) facing the floor and a wall, plus a walk that logs section rebuilds.
 */
public final class LightScenario {
    private static final double[] DENSITIES = {0.0, 0.4, 0.7, 1.0};
    private static final String[] COLORS = {"red", "green", "blue", "purple", "orange"};
    private static final double[] origin = new double[3];
    private static int rebuiltMark;

    private LightScenario() {}

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
            driver.command("time set 18000");
            driver.command("gamemode creative");
            FogClient.setTestOverride(0.0);
        }));
        steps.add(new ClientTestDriver.Step("origin", 20, () -> {
            origin[0] = Math.floor(mc.player.getX()) + 0.5;
            origin[1] = Math.floor(mc.player.getY());
            origin[2] = Math.floor(mc.player.getZ()) + 0.5;
            ClientTestDriver.log("origin " + origin[0] + " " + origin[1] + " " + origin[2] + ", chunk at +60 loaded: "
                + mc.level.getChunkSource().hasChunk(((int) origin[0]) >> 4, ((int) origin[2] + 60) >> 4));
        }));
        // A stone platform 49 blocks wide running 66 blocks away from the spawn, air above it.
        steps.add(new ClientTestDriver.Step("platform", 100, () -> {
            fill(driver, -24, -1, -2, 24, -1, 64, "minecraft:stone");
            fill(driver, -24, 0, -2, 24, 9, 30, "minecraft:air");
            fill(driver, -24, 0, 31, 24, 9, 64, "minecraft:air");
        }));
        steps.add(new ClientTestDriver.Step("hide-hud", 20, () -> {
            mc.options.hideGui = true;
            mc.gui.getChat().clearMessages(true);
        }));
        steps.add(new ClientTestDriver.Step("far-lamps", 160, () -> {
            // Distance 30, 45 and 60 in front of the camera, every lamp on its own bearing so none hides another.
            lamp(driver, -14, 30, "red");
            lamp(driver, -4, 30, "green");
            lamp(driver, 16, 30, "purple");
            lamp(driver, -9, 45, "orange");
            fill(driver, 0, 0, 45, 0, 2, 45, "minecraft:stone"); // a small pillar
            lamp(driver, 0, 45, 3, "blue");
            lamp(driver, 9, 45, "red");
            lamp(driver, -24, 60, "green");
            lamp(driver, 6, 60, "purple");
            lamp(driver, 24, 60, "orange");
        }));
        steps.add(new ClientTestDriver.Step("look-far", 40, () -> look(mc, 0f, 3f)));
        for (double density : DENSITIES) {
            String tag = String.format(Locale.ROOT, "%03d", Math.round(density * 100));
            steps.add(new ClientTestDriver.Step("far-fog-" + tag, 80, () -> FogClient.setTestOverride(density)));
            steps.add(new ClientTestDriver.Step("far-fog-" + tag + "-shot", 10, () -> driver.shot("f-far-fog-" + tag)));
        }
        steps.add(new ClientTestDriver.Step("fog-off", 40, () -> FogClient.setTestOverride(0.0)));

        // Five lamps close by: the shape of the light pools and their rim.
        steps.add(new ClientTestDriver.Step("near-lamps", 140, () -> {
            for (int i = 0; i < COLORS.length; i++) {
                lamp(driver, -8 + 4 * i, 6 + (i == 0 || i == 4 ? -1 : i == 2 ? 3 : 2), COLORS[i]);
            }
            look(mc, 0f, 12f);
        }));
        steps.add(new ClientTestDriver.Step("near-lamps-shot", 10, () -> driver.shot("l1-lamps")));
        steps.add(new ClientTestDriver.Step("near-lamps-top", 40, () -> {
            driver.command(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f 0 55", origin[0], origin[1] + 14, origin[2] + 3));
        }));
        steps.add(new ClientTestDriver.Step("near-lamps-top-shot", 10, () -> driver.shot("l2-lamps-top")));

        addHeld(steps, driver, mc);

        steps.add(new ClientTestDriver.Step("done", 0, () -> {
            ClientTestDriver.log("DONE");
            mc.stop();
        }));
    }

    private static void addHeld(List<ClientTestDriver.Step> steps, ClientTestDriver driver, Minecraft mc) {
        // A dark spot away from the lamps, a wall three blocks ahead.
        steps.add(new ClientTestDriver.Step("held-spot", 80, () -> {
            tp(driver, -20, 0, 0, 0f, 0f);
            fill(driver, -23, 0, 3, -17, 4, 3, "minecraft:stone");
        }));
        steps.add(new ClientTestDriver.Step("held-none", 40, () -> {
            hold(driver, "minecraft:air");
            look(mc, 0f, 45f);
        }));
        steps.add(new ClientTestDriver.Step("held-none-shot", 10, () -> driver.shot("h0-nothing")));
        String[][] items = {
            {"minecraft:soul_torch", "h1-soul-torch"},
            {"minecraft:torch", "h2-torch"},
            {"incognita:lamp_red", "h3-lamp-red"},
            {"incognita:lamp_green", "h4-lamp-green"}};
        for (String[] item : items) {
            steps.add(new ClientTestDriver.Step("hold-" + item[1], 60, () -> {
                hold(driver, item[0]);
                look(mc, 0f, 50f);
            }));
            steps.add(new ClientTestDriver.Step(item[1] + "-floor", 6, () -> driver.shot(item[1] + "-floor")));
            steps.add(new ClientTestDriver.Step(item[1] + "-wall-look", 30, () -> look(mc, 0f, 5f)));
            steps.add(new ClientTestDriver.Step(item[1] + "-wall", 6, () -> driver.shot(item[1] + "-wall")));
        }
        // Walk with a soul torch, 4 blocks per second, and report the section rebuild rate.
        steps.add(new ClientTestDriver.Step("walk-prep", 40, () -> {
            hold(driver, "minecraft:soul_torch");
            tp(driver, -20, 0, -1, -90f, 40f);
        }));
        steps.add(new ClientTestDriver.Step("walk-mark", 1, () -> {
            rebuiltMark = LightingClient.rebuiltTotal();
            ClientTestDriver.log("walk start: " + stats());
        }));
        for (int i = 1; i <= 40; i++) {
            double x = -20 + i * 0.25;
            steps.add(new ClientTestDriver.Step("walk-" + i, 4, () -> {
                tp(driver, x, 0, -1, -90f, 40f);
                if (((int) Math.round((x + 20) * 4)) % 20 == 0) {
                    ClientTestDriver.log("walk x=" + x + " rebuilt in the last second(s): "
                        + (LightingClient.rebuiltTotal() - rebuiltMark) + " " + stats());
                    rebuiltMark = LightingClient.rebuiltTotal();
                }
            }));
        }
        steps.add(new ClientTestDriver.Step("walk-shot", 10, () -> driver.shot("h5-walk-end")));
        steps.add(new ClientTestDriver.Step("walk-stop", 40, () -> ClientTestDriver.log("after stopping: " + stats())));
        steps.add(new ClientTestDriver.Step("walk-stop-shot", 10, () -> driver.shot("h6-walk-stopped")));
    }

    private static String stats() {
        ColorLightField f = LightingClient.field();
        return f == null ? "no field" : f.emitterCount() + " emitters, " + f.sectionCount() + " sections";
    }

    private static void hold(ClientTestDriver driver, String item) {
        driver.command("item replace entity @s weapon.mainhand with " + item);
    }

    private static void tp(ClientTestDriver driver, double dx, double dy, double dz, float yaw, float pitch) {
        driver.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f",
            origin[0] + dx, origin[1] + dy, origin[2] + dz, yaw, pitch));
    }

    private static void lamp(ClientTestDriver driver, int dx, int dz, String color) {
        lamp(driver, dx, dz, 0, color);
    }

    private static void lamp(ClientTestDriver driver, int dx, int dz, int dy, String color) {
        driver.command(String.format(Locale.ROOT, "setblock %d %d %d incognita:lamp_%s", (int) Math.floor(origin[0]) + dx,
            (int) origin[1] + dy, (int) Math.floor(origin[2]) + dz, color));
    }

    private static void fill(ClientTestDriver driver, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        int bx = (int) Math.floor(origin[0]);
        int by = (int) origin[1];
        int bz = (int) Math.floor(origin[2]);
        driver.command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", bx + x1, by + y1, bz + z1, bx + x2, by + y2,
            bz + z2, block));
    }

    private static void look(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
    }
}
