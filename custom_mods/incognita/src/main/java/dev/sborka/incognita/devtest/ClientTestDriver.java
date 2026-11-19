package dev.sborka.incognita.devtest;

import dev.sborka.incognita.Incognita;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dev-only scenario runner: enabled by {@code -Dincognita.clientTest=1}. Creates a world, walks through a fixed list
 * of GUI steps, takes screenshots into {@code <gameDir>/screenshots/ct-*.png}, logs {@code [clientTest]} lines and
 * quits. Replaces external keyboard automation, which loses window focus under Hyprland.
 */
public final class ClientTestDriver {
    public static final String PROPERTY = "incognita.clientTest";
    private static final String WORLD = "clienttest";
    private static final String VOID_WORLD = "clienttest-void";
    /** The clouds scenario has its own normal world, so its views are not changed by the other scenarios. */
    private static final String CLOUDS_SCENARIO = "clouds";
    private static final String CLOUDS_WORLD = "clienttest-clouds";
    /** Scenario names that run in the reused empty flat world instead of the normal one. */
    private static final String VOID_SCENARIO = "skyvoid";

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Step> steps = new ArrayList<>();
    private int index;
    private int wait;
    private boolean worldRequested;
    private boolean voidWorld;
    private String worldName = WORLD;

    @FunctionalInterface
    public interface Action {
        void run() throws Exception;
    }

    public record Step(String name, int delayAfter, Action action) {}

    private ClientTestDriver() {
        if (addScenarioSteps(scenario())) {
            return;
        }
        steps.add(new Step("settle", 100, () -> {
            mc.options.tutorialStep = TutorialSteps.NONE;
            mc.getTutorial().stop();
            mc.options.pauseOnLostFocus = false;
            mc.options.onboardAccessibility = false;
            log("player in world, pos hidden check follows");
        }));
        steps.add(new Step("world", 10, () -> shot("world")));
        steps.add(new Step("f3-on", 30, () -> mc.getDebugOverlay().toggleOverlay()));
        steps.add(new Step("f3-shot", 5, () -> shot("f3")));
        steps.add(new Step("f3-off", 10, () -> mc.getDebugOverlay().toggleOverlay()));
        steps.add(new Step("give", 40, () -> {
            command("give @s compass");
            command("give @s incognita:origin_compass");
            command("give @s incognita:radio");
        }));
        steps.add(new Step("compass-angles", 5, this::logCompassAngles));
        steps.add(new Step("inventory", 15, () -> mc.setScreen(
            new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player))));
        steps.add(new Step("inventory-shot", 5, () -> shot("inventory")));
        steps.add(new Step("creative-open", 20, () -> mc.setScreen(
            new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true))));
        steps.add(new Step("creative-tab", 20, this::selectIncognitaTab));
        steps.add(new Step("creative-shot", 5, () -> shot("creative-tab")));
        steps.add(new Step("quests-open", 40, this::openQuests));
        steps.add(new Step("close", 10, () -> mc.setScreen(null)));
        steps.add(new Step("done", 0, () -> {
            log("DONE");
            mc.stop();
        }));
    }

    /**
     * Looks up {@code devtest.scenario.<Name>Scenario} by the scenario name and lets it add its steps. Each scenario is a
     * class with {@code static void addSteps(List<Step>, ClientTestDriver)}. Returns false for the default GUI run.
     */
    private boolean addScenarioSteps(String name) {
        if (name.isBlank() || "1".equals(name)) {
            return false;
        }
        voidWorld = VOID_SCENARIO.equals(name);
        worldName = voidWorld ? VOID_WORLD : name.startsWith(CLOUDS_SCENARIO) ? CLOUDS_WORLD : WORLD;
        String base = voidWorld ? "sky" : name;
        String cls = "dev.sborka.incognita.devtest.scenario." + Character.toUpperCase(base.charAt(0)) + base.substring(1)
            + "Scenario";
        try {
            Class<?> type;
            try {
                type = Class.forName(cls);
            } catch (ClassNotFoundException missing) {
                // Further scenarios live in a subpackage to keep the package within its file limit.
                type = Class.forName(cls.replace(".scenario.", ".scenario.extra."));
            }
            Method add = type.getDeclaredMethod("addSteps", List.class, ClientTestDriver.class);
            add.invoke(null, steps, this);
            return true;
        } catch (ReflectiveOperationException e) {
            log("unknown scenario '" + name + "' (" + cls + "): " + e);
            return false;
        }
    }

    /** Scenario name from the system property value or from the content of the flag file; blank means the default. */
    private static String scenario() {
        String value = System.getProperty(PROPERTY, "");
        try {
            java.nio.file.Path flag = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("incognita-clienttest.flag");
            if (java.nio.file.Files.exists(flag)) {
                value = java.nio.file.Files.readString(flag).trim();
            }
        } catch (java.io.IOException e) {
            log("could not read the flag file: " + e.getMessage());
        }
        return value;
    }

    public static void register() {
        NeoForge.EVENT_BUS.register(new ClientTestDriver());
        Incognita.LOGGER.info("[clientTest] driver enabled");
    }

    @net.neoforged.bus.api.SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        if (mc.level == null || mc.player == null) {
            if (mc.screen instanceof TitleScreen && !worldRequested) {
                worldRequested = true;
                createWorld();
            }
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (index >= steps.size()) {
            return;
        }
        Step step = steps.get(index++);
        log("step " + step.name());
        try {
            step.action().run();
        } catch (Throwable t) {
            Incognita.LOGGER.error("[clientTest] step {} failed", step.name(), t);
        }
        wait = step.delayAfter();
    }

    /** True when the scenario runs in the empty world: no terrain, no clouds, only the sky. */
    public boolean isVoidWorld() {
        return voidWorld;
    }

    /** Reuses the scenario's world between runs so screenshots compare the same place. */
    private void createWorld() {
        String name = worldName;
        if (mc.getLevelSource().levelExists(name)) {
            log("loading the existing world " + name);
            mc.createWorldOpenFlows().openWorld(name, () -> { });
            return;
        }
        log("creating world " + name);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(12345L, false, false),
            voidWorld ? ClientTestDriver::voidDimensions : WorldPresets::createNormalWorldDimensions, mc.screen);
    }

    /** Overworld with a flat generator that has no layers: nothing but sky. */
    private static net.minecraft.world.level.levelgen.WorldDimensions voidDimensions(
        net.minecraft.core.RegistryAccess access) {
        var biome = access.registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
            .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var flat = new net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings(
            java.util.Optional.empty(), biome, List.of());
        flat.updateLayers();
        return WorldPresets.createNormalWorldDimensions(access).replaceOverworldGenerator(access,
            new net.minecraft.world.level.levelgen.FlatLevelSource(flat));
    }

    /** Opens the FTB Quests book by reflection (soft dependency) and queues one shot per chapter plus one quest view. */
    private void openQuests() throws Exception {
        Class<?> fileClass;
        try {
            fileClass = Class.forName("dev.ftb.mods.ftbquests.client.ClientQuestFile");
        } catch (ClassNotFoundException e) {
            log("FTB Quests not installed, skipping quests");
            return;
        }
        Object file = fileClass.getField("INSTANCE").get(null);
        if (file == null) {
            log("ClientQuestFile.INSTANCE is null: quests not synced");
            return;
        }
        Object screen = fileClass.getMethod("openGui").invoke(null);
        List<?> chapters = (List<?>) file.getClass().getMethod("getAllChapters").invoke(file);
        log("quests: " + chapters.size() + " chapters");
        int at = index;
        int n = 0;
        for (Object chapter : chapters) {
            String title = chapterTitle(chapter);
            int num = n++;
            steps.add(at++, new Step("chapter-" + num, 12, () -> {
                invoke(screen, "selectChapter", chapter);
                log("chapter " + num + ": " + title);
            }));
            steps.add(at++, new Step("chapter-" + num + "-shot", 3, () -> shot("quests-ch" + num)));
            if (num == 1) {
                steps.add(at++, new Step("quest-view", 12, () -> {
                    List<?> quests = (List<?>) invoke(chapter, "getQuests");
                    if (!quests.isEmpty()) {
                        invoke(screen, "viewQuest", quests.get(0));
                    }
                }));
                steps.add(at++, new Step("quest-view-shot", 3, () -> shot("quest-view")));
                steps.add(at++, new Step("quest-view-close", 5, () -> invoke(screen, "closeQuest")));
            }
        }
    }

    private static String chapterTitle(Object chapter) {
        try {
            return ((Component) invoke(chapter, "getTitle")).getString();
        } catch (Exception e) {
            return "?";
        }
    }

    /** Calls a public one-argument-or-less method by name, ignoring overload parameter types. */
    private static Object invoke(Object target, String name, Object... args) {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    return m.invoke(target, args);
                } catch (Exception e) {
                    throw new IllegalStateException(name, e);
                }
            }
        }
        throw new IllegalStateException("no method " + name);
    }

    public void command(String cmd) {
        mc.player.connection.sendCommand(cmd);
    }

    public void shot(String name) {
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, "ct-" + name + ".png", mc.getMainRenderTarget(),
            msg -> log("screenshot " + name + ": " + msg.getString()));
    }

    /** Logs the needle angle of both compasses; 0..1 fraction of a turn as the model predicate sees it. */
    private void logCompassAngles() {
        Player player = mc.player;
        for (var entry : List.of("minecraft:compass", "incognita:origin_compass")) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry));
            ItemStack stack = new ItemStack(item);
            var prop = ItemProperties.getProperty(stack, ResourceLocation.withDefaultNamespace("angle"));
            float value = prop == null ? Float.NaN : prop.call(stack, mc.level, player, 0);
            log(entry + " angle=" + value + " playerYaw=" + player.getYRot());
            if (entry.startsWith("incognita:")) {
                double toOrigin = Math.atan2(-player.getZ(), -player.getX()) / (Math.PI * 2);
                double yaw = player.getYRot() / 360.0;
                double expected = 0.5 - (yaw - Math.floor(yaw) - 0.25 - toOrigin);
                expected -= Math.floor(expected);
                log("origin compass check: real pos " + player.position() + " expected angle=" + expected
                    + " actual=" + value + " delta=" + Math.abs(expected - value));
            }
        }
    }

    /** Switches the creative screen to the Terra Incognita tab through its private selectTab method. */
    private void selectIncognitaTab() {
        try {
            doSelectIncognitaTab();
        } catch (Exception e) {
            Incognita.LOGGER.error("[clientTest] tab selection failed", e);
        }
    }

    private void doSelectIncognitaTab() throws Exception {
        CreativeModeTab target = null;
        for (CreativeModeTab tab : net.minecraft.world.item.CreativeModeTabs.allTabs()) {
            if (tab.getDisplayName().getString().contains("Incognita")) {
                target = tab;
            }
        }
        if (target == null) {
            log("Terra Incognita tab NOT FOUND among " + net.minecraft.world.item.CreativeModeTabs.allTabs().size());
            return;
        }
        for (Method m : CreativeModeInventoryScreen.class.getDeclaredMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == CreativeModeTab.class
                && "selectTab".equals(m.getName())) {
                m.setAccessible(true);
                m.invoke(mc.screen, target);
                log("selected tab " + target.getDisplayName().getString());
                return;
            }
        }
        log("selectTab not found");
    }

    public static void log(String msg) {
        Incognita.LOGGER.info("[clientTest] {}", msg);
    }
}
