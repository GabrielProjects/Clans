package dev.clans.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private MessageUtil() {
    }

    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(builder, replacement.toString());
        }
        matcher.appendTail(builder);
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }

    public static Component toComponent(String message) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }
        return parseLegacy(colorize(message));
    }

    private static Component parseLegacy(String legacy) {
        TextComponent.Builder builder = Component.text();
        Style.Builder style = Style.style();
        StringBuilder segment = new StringBuilder();

        for (int i = 0; i < legacy.length(); i++) {
            char ch = legacy.charAt(i);
            if (ch != '§' || i + 1 >= legacy.length()) {
                segment.append(ch);
                continue;
            }

            char code = legacy.charAt(++i);
            if (code == 'x' && i + 12 < legacy.length()) {
                flushSegment(builder, segment, style);
                String hex = ""
                        + legacy.charAt(i + 2)
                        + legacy.charAt(i + 4)
                        + legacy.charAt(i + 6)
                        + legacy.charAt(i + 8)
                        + legacy.charAt(i + 10)
                        + legacy.charAt(i + 12);
                i += 12;
                style = Style.style().color(TextColor.fromHexString("#" + hex));
                continue;
            }

            flushSegment(builder, segment, style);
            style = styleFromCode(code);
        }

        flushSegment(builder, segment, style);
        return builder.build();
    }

    private static void flushSegment(TextComponent.Builder builder, StringBuilder segment, Style.Builder style) {
        if (segment.isEmpty()) {
            return;
        }
        builder.append(Component.text(segment.toString(), style.build()));
        segment.setLength(0);
    }

    private static Style.Builder styleFromCode(char code) {
        ChatColor chatColor = ChatColor.getByChar(code);
        if (chatColor == null) {
            return Style.style();
        }
        if (chatColor == ChatColor.RESET) {
            return Style.style();
        }
        if (chatColor.isColor()) {
            TextColor textColor = chatColorToTextColor(chatColor);
            return textColor != null ? Style.style().color(textColor) : Style.style();
        }
        return switch (chatColor) {
            case BOLD -> Style.style().decorate(TextDecoration.BOLD);
            case ITALIC -> Style.style().decorate(TextDecoration.ITALIC);
            case UNDERLINE -> Style.style().decorate(TextDecoration.UNDERLINED);
            case STRIKETHROUGH -> Style.style().decorate(TextDecoration.STRIKETHROUGH);
            case MAGIC -> Style.style().decorate(TextDecoration.OBFUSCATED);
            default -> Style.style();
        };
    }

    private static TextColor chatColorToTextColor(ChatColor chatColor) {
        return switch (chatColor) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    public static void runSync(Runnable runnable) {
        Bukkit.getScheduler().runTask(dev.clans.ClansPlugin.getInstance(), runnable);
    }

    public static void runAsync(Runnable runnable) {
        Bukkit.getScheduler().runTaskAsynchronously(dev.clans.ClansPlugin.getInstance(), runnable);
    }
}
