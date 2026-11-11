package dev.sborka.incognita.coordinates.rewrite;

import dev.sborka.incognita.coordinates.Spoofer;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Replaces coordinates inside chat components with Spoofer values. Pure: no game state. */
public final class ComponentRewriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentRewriter.class);

    private static final String NUM = "-?\\d+(?:[.,]\\d+)?";
    private static final String SEP = "(?:\\s*[,;]\\s*|\\s+)";
    private static final Pattern TRIPLE = Pattern.compile(
        "(?<![\\w.])[\\[(]?" + NUM + "(?:" + SEP + NUM + "){2,}[\\])]?(?![\\w.])");
    private static final String NBT_NUM = NUM + "[bBsSlLfFdDI]?";
    private static final Pattern NBT_TRIPLE = Pattern.compile(
        "(?<![\\w.])[\\[(]?" + NBT_NUM + "(?:" + SEP + NBT_NUM + "){2,}[\\])]?(?![\\w.])");
    /** Chunk positions as vanilla prints them: "[x, z]" or a list "[0, 0], [1, 0]". Used only for arguments of listed keys. */
    private static final Pattern CHUNK_PAIRS = Pattern.compile(
        "\\s*\\[-?\\d+,\\s*-?\\d+\\](?:,\\s*\\[-?\\d+,\\s*-?\\d+\\])*\\s*");
    private static final Pattern LABELED = Pattern.compile(
        "(?i)\\bx\\s*[=:]\\s*" + NUM + "\\s*[,;]?\\s*y\\s*[=:]\\s*" + NUM + "\\s*[,;]?\\s*z\\s*[=:]\\s*" + NUM);
    private static final Pattern NUMERIC = Pattern.compile(NUM);
    private static final Pattern TELEPORT = Pattern.compile("(?s).*\\b(?:tp|teleport)\\b.*");
    private static final String COMMANDS_PREFIX = "commands.";
    private static final String VANILLA_COORDINATES_KEY = "chat.coordinates";

    private final Spoofer spoofer;
    private final Supplier<Set<String>> translationKeys;
    private final AtomicBoolean warned = new AtomicBoolean();

    public ComponentRewriter(Spoofer spoofer, Supplier<Set<String>> translationKeys) {
        this.spoofer = spoofer;
        this.translationKeys = translationKeys;
    }

    /** Like {@link #rewrite}, but never throws: on failure logs once and returns the original. */
    public Component rewriteSafely(Component component) {
        try {
            return rewrite(component);
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            if (warned.compareAndSet(false, true)) {
                LOGGER.warn("Coordinate rewriting failed, passing messages through unchanged", e);
            }
            return component;
        }
    }

    boolean hasWarned() {
        return warned.get();
    }

    public Component rewrite(Component component) {
        Component flattened = flattenNumberRuns(component);
        if (flattened != null) {
            return flattened;
        }
        MutableComponent copy = rewriteContents(component);
        copy.setStyle(rewriteStyle(component.getStyle()));
        for (Component sibling : component.getSiblings()) {
            copy.append(rewrite(sibling));
        }
        return copy;
    }

    /**
     * NbtUtils.toPrettyComponent emits one flat component whose siblings are single tokens ("[", "1.5", "d", ",", ...), so a
     * position such as Pos:[1.5d,64.0d,-3.2d] never matches the 3-number regex per token. When an empty container holds only
     * plain leaf siblings, test their concatenation (type suffixes allowed) and replace it by one literal if it holds a triple.
     * Trade-off: that one component loses its syntax colouring; every other component is untouched.
     */
    private Component flattenNumberRuns(Component component) {
        List<Component> siblings = component.getSiblings();
        if (siblings.size() < 3 || component.getContents() != PlainTextContents.EMPTY) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (Component sibling : siblings) {
            Style style = sibling.getStyle();
            if (!(sibling.getContents() instanceof PlainTextContents plain) || !sibling.getSiblings().isEmpty()
                || style.getClickEvent() != null || style.getHoverEvent() != null) {
                return null;
            }
            text.append(plain.text());
        }
        String original = text.toString();
        String replaced = NBT_TRIPLE.matcher(original).replaceAll(Matcher.quoteReplacement(spoofer.text()));
        if (replaced.equals(original)) {
            return null;
        }
        return Component.literal(replaced).setStyle(rewriteStyle(component.getStyle()));
    }

    private MutableComponent rewriteContents(Component component) {
        ComponentContents contents = component.getContents();
        if (contents instanceof TranslatableContents translatable) {
            if (VANILLA_COORDINATES_KEY.equals(translatable.getKey())) {
                return Component.literal(spoofer.text());
            }
            boolean listed = isListed(translatable.getKey());
            Object[] args = translatable.getArgs();
            Object[] rewritten = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                rewritten[i] = rewriteArg(args[i], listed);
            }
            if (translatable.getKey().startsWith(COMMANDS_PREFIX)) {
                scrubNumericRuns(args, rewritten);
            }
            return MutableComponent.create(new TranslatableContents(translatable.getKey(), translatable.getFallback(), rewritten));
        }
        if (contents instanceof PlainTextContents plain) {
            return Component.literal(replaceTriples(plain.text()));
        }
        return MutableComponent.create(contents);
    }

    private Object rewriteArg(Object arg, boolean listed) {
        if (listed && arg instanceof Component number && isPlainNumber(number)) {
            return Component.literal(spoofer.text()).setStyle(number.getStyle().withClickEvent(null).withHoverEvent(null));
        }
        if (listed) {
            Object masked = maskNumericArg(arg, spoofer.text());
            if (masked != arg) {
                return masked;
            }
        }
        if (arg instanceof Component nested) {
            return rewrite(nested);
        }
        if (arg instanceof String text) {
            return replaceTriples(text);
        }
        return arg;
    }

    /**
     * Placeholder for a number, a numeric string or a numeric literal component; the same object for anything else.
     * Shared by the rewriter and the construction-time hook so both mask identically.
     */
    static Object maskNumericArg(Object arg, String placeholder) {
        if (arg instanceof Number) {
            return placeholder;
        }
        if (arg instanceof String text && (NUMERIC.matcher(text).matches() || CHUNK_PAIRS.matcher(text).matches())) {
            return placeholder;
        }
        if (arg instanceof String text && (TRIPLE.matcher(text).find() || LABELED.matcher(text).find())) {
            // pre-formatted position strings such as "minecraft:overworld / [10,64,-20]"
            return replaceTriples(text, placeholder);
        }
        if (arg instanceof Component nested && isPlainNumber(nested)) {
            return Component.literal(placeholder).setStyle(nested.getStyle().withClickEvent(null).withHoverEvent(null));
        }
        return arg;
    }

    /**
     * Masks numeric arguments when {@code key} matches one of {@code patterns}. Returns {@code args} itself when nothing
     * needed masking, otherwise a copy; never mutates the input.
     */
    public static Object[] maskArgsForKey(String key, Object[] args, Collection<String> patterns, Supplier<String> placeholder) {
        if (key == null || args == null || args.length == 0) {
            return args;
        }
        boolean anyCandidate = false;
        for (Object arg : args) {
            if (arg instanceof Number || arg instanceof String || arg instanceof Component) {
                anyCandidate = true;
                break;
            }
        }
        if (!anyCandidate || !isListed(patterns, key)) {
            return args;
        }
        String text = placeholder.get();
        Object[] result = null;
        for (int i = 0; i < args.length; i++) {
            Object masked = maskNumericArg(args[i], text);
            if (masked != args[i]) {
                if (result == null) {
                    result = args.clone();
                }
                result[i] = masked;
            }
        }
        return result == null ? args : result;
    }

    public static boolean isListed(Collection<String> patterns, String key) {
        for (String pattern : patterns) {
            if (pattern.endsWith("*") ? key.startsWith(pattern.substring(0, pattern.length() - 1)) : key.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    /** A literal component with no siblings that holds only a number, e.g. Create's LangBuilder number arguments. */
    static boolean isPlainNumber(Component component) {
        return component.getSiblings().isEmpty()
            && component.getContents() instanceof PlainTextContents plain
            && (NUMERIC.matcher(plain.text().trim()).matches() || CHUNK_PAIRS.matcher(plain.text()).matches());
    }

    private static boolean isNumericArg(Object arg) {
        return arg instanceof Number || (arg instanceof String text && NUMERIC.matcher(text.trim()).matches());
    }

    /** In vanilla command feedback (commands.*), any run of 3+ numeric arguments is a coordinate triple. */
    private void scrubNumericRuns(Object[] original, Object[] rewritten) {
        int i = 0;
        while (i < original.length) {
            if (!isNumericArg(original[i])) {
                i++;
                continue;
            }
            int end = i;
            while (end < original.length && isNumericArg(original[end])) {
                end++;
            }
            if (end - i >= 3) {
                for (int j = i; j < end; j++) {
                    rewritten[j] = spoofer.text();
                }
            }
            i = end;
        }
    }

    private String replaceTriples(String text) {
        return replaceTriples(text, spoofer.text());
    }

    static String replaceTriples(String text, String placeholder) {
        String replacement = Matcher.quoteReplacement(placeholder);
        String labeled = LABELED.matcher(text).replaceAll(replacement);
        return TRIPLE.matcher(labeled).replaceAll(replacement);
    }

    private boolean isListed(String key) {
        return isListed(translationKeys.get(), key);
    }

    private Style rewriteStyle(Style style) {
        ClickEvent click = style.getClickEvent();
        if (click != null && isCoordinateClick(click)) {
            return style.withClickEvent(null).withHoverEvent(null);
        }
        HoverEvent hover = style.getHoverEvent();
        if (hover != null && hover.getAction() == HoverEvent.Action.SHOW_TEXT) {
            Component text = hover.getValue(HoverEvent.Action.SHOW_TEXT);
            if (text != null) {
                return style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, rewrite(text)));
            }
        }
        return style;
    }

    private static boolean isCoordinateClick(ClickEvent click) {
        boolean command = click.getAction() == ClickEvent.Action.RUN_COMMAND
            || click.getAction() == ClickEvent.Action.SUGGEST_COMMAND
            || click.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD;
        if (!command) {
            return false;
        }
        String value = click.getValue();
        return TELEPORT.matcher(value).matches() || TRIPLE.matcher(value).find() || LABELED.matcher(value).find();
    }
}
