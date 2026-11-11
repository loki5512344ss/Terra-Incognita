package dev.sborka.incognita.coordinates.guard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;

class KubeJsCommandGuardTest {
    private static CommandDispatcher<Object> dispatcher() {
        CommandDispatcher<Object> d = new CommandDispatcher<>();
        var kubejs = d.register(LiteralArgumentBuilder.<Object>literal("kubejs")
            .then(LiteralArgumentBuilder.<Object>literal("hand").executes(c -> 1))
            .then(LiteralArgumentBuilder.<Object>literal("inventory").executes(c -> 1))
            .then(LiteralArgumentBuilder.<Object>literal("reload").executes(c -> 1)));
        d.register(LiteralArgumentBuilder.<Object>literal("kjs").redirect(kubejs));
        d.register(LiteralArgumentBuilder.<Object>literal("execute")
            .then(LiteralArgumentBuilder.<Object>literal("run").redirect(d.getRoot())));
        return d;
    }

    @Test
    void itemDumpsAreDetectedIncludingAliasAndExecuteRun() {
        CommandDispatcher<Object> d = dispatcher();
        assertTrue(KubeJsCommandGuard.isItemDump(d.parse("kubejs hand", new Object())));
        assertTrue(KubeJsCommandGuard.isItemDump(d.parse("kubejs inventory", new Object())));
        assertTrue(KubeJsCommandGuard.isItemDump(d.parse("kjs hand", new Object())));
        assertTrue(KubeJsCommandGuard.isItemDump(d.parse("execute run kubejs hand", new Object())));
    }

    @Test
    void otherKubeJsCommandsAreNotAffected() {
        CommandDispatcher<Object> d = dispatcher();
        assertFalse(KubeJsCommandGuard.isItemDump(d.parse("kubejs reload", new Object())));
        assertFalse(KubeJsCommandGuard.isItemDump(d.parse("execute run kubejs reload", new Object())));
    }
}
