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
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.DataPackConfig;
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

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Step> steps = new ArrayList<>();
    private int index;
    private int wait;
    private boolean worldRequested;

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private record Step(String name, int delayAfter, Action action) {}

    private ClientTestDriver() {
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

    private void createWorld() {
        log("creating world " + WORLD);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
            new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(12345L, false, false),
            WorldPresets::createNormalWorldDimensions, mc.screen);
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

    private void command(String cmd) {
        mc.player.connection.sendCommand(cmd);
    }

    private void shot(String name) {
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
                && m.getName().equals("selectTab")) {
                m.setAccessible(true);
                m.invoke(mc.screen, target);
                log("selected tab " + target.getDisplayName().getString());
                return;
            }
        }
        log("selectTab not found");
    }

    private static void log(String msg) {
        Incognita.LOGGER.info("[clientTest] {}", msg);
    }
}
