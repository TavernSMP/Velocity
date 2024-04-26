/*
 * Copyright (C) 2018-2024 Velocity Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.velocitypowered.proxy.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

/**
 * Serialize a component to a string.
 *
 * @author Elmar Blume
 */
public final class ComponentUtils {

  // MiniMessage default: <#FFFFFF>
  private static final Pattern STANDARD_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]){6}>");
  private static final List<Pattern> ODD_HEX_PATTERNS = Arrays.asList(
        Pattern.compile("<&#([A-Fa-f0-9]){6}>"),  // <&#FFFFFF>
        STANDARD_HEX_PATTERN,
        Pattern.compile("&#([A-Fa-f0-9]){6}"),    // &#FFFFFF
        Pattern.compile("#([A-Fa-f0-9]){6}")      // #FFFFFF
  ); // <!> order matters

  private static final MiniMessage MINI = MiniMessage.builder()
          .strict(false)
          .build();

  private static final Map<String, String> COLOR_MAP = new HashMap<>();

  static {
    COLOR_MAP.put("§", "&");
    COLOR_MAP.put("&0", "<reset><black>");
    COLOR_MAP.put("&1", "<reset><dark_blue>");
    COLOR_MAP.put("&2", "<reset><dark_green>");
    COLOR_MAP.put("&3", "<reset><dark_aqua>");
    COLOR_MAP.put("&4", "<reset><dark_red>");
    COLOR_MAP.put("&5", "<reset><dark_purple>");
    COLOR_MAP.put("&6", "<reset><gold>");
    COLOR_MAP.put("&7", "<reset><gray>");
    COLOR_MAP.put("&8", "<reset><dark_gray>");
    COLOR_MAP.put("&9", "<reset><blue>");
    COLOR_MAP.put("&a", "<reset><green>");
    COLOR_MAP.put("&b", "<reset><aqua>");
    COLOR_MAP.put("&c", "<reset><red>");
    COLOR_MAP.put("&d", "<reset><light_purple>");
    COLOR_MAP.put("&e", "<reset><yellow>");
    COLOR_MAP.put("&f", "<reset><white>");
    COLOR_MAP.put("&k", "<obfuscated>");
    COLOR_MAP.put("&l", "<bold>");
    COLOR_MAP.put("&m", "<strikethrough>");
    COLOR_MAP.put("&n", "<underlined>");
    COLOR_MAP.put("&o", "<italic>");
    COLOR_MAP.put("&r", "<reset>");
    COLOR_MAP.put("\\n", "<newline>");
  }

  private ComponentUtils() {
    throw new AssertionError("Instances of this class should not be created.");
  }

  /**
   * Serialize a component to a string.
   *
   * @param component the component to serialize
   * @return the serialized component
   */
  public static @NotNull String serializeComponent(Component component) {
    return MINI.serialize(component);
  }

  /**
   * Parses a string to a component.
   *
   * @param input the string to parse
   * @return the parsed component
   */
  public static @NotNull Component parseComponent(String input) {
    return MINI.deserialize(colorifyLegacy(input));
  }

  /**
   * Colorify component parsing hex patterns.
   *
   * @param input the string to colorify into a component
   * @return the colorized component
   */
  public static @NotNull Component colorify(String input) {
    if (input == null) {
      return Component.empty();
    }

    String parsedStr = input;

    // Parse the hex patterns
    for (Pattern pattern : ODD_HEX_PATTERNS) {
      final Matcher matcher = pattern.matcher(parsedStr);

      while (matcher.find()) {
        // Replace the odd hex pattern with a standard hex pattern
        final String match = matcher.group();
        final String hexValue = match.substring(match.indexOf("#") + 1, match.indexOf("#") + 7);

        parsedStr = parsedStr.replace(match, "<#DONE" + hexValue.toUpperCase() + ">");
      }
    }

    // Remove the 'DONE' from all the hex patterns
    final Pattern donePattern = Pattern.compile("<#DONE([A-Fa-f0-9]){6}>");
    final Matcher doneMatcher = donePattern.matcher(parsedStr);
    while (doneMatcher.find()) {
      // Get rid of the 'DONE' in the hex pattern
      final String match = doneMatcher.group();
      final String hexValue = match.substring(match.indexOf("#") + 5, match.indexOf("#") + 11);

      parsedStr = parsedStr.replace(match, "<#" + hexValue + ">");
    }

    // Parse the final component
    return parseComponent(parsedStr);
  }

  /**
   * Colorify legacy parsing legacy color codes.
   *
   * @param input the string to colorify into a component
   * @return the colorized component
   */
  public static String colorifyLegacy(String input) {
    String parsedStr = input;

    for (Map.Entry<String, String> entry : COLOR_MAP.entrySet()) {
      parsedStr = parsedStr.replace(entry.getKey(), entry.getValue());
    }

    return parsedStr;
  }

  /**
   * Strips matching hex patterns from a string.
   *
   * @param input the input
   * @return the string
   */
  public static String stripHex(String input) {
    for (Pattern pattern : ODD_HEX_PATTERNS) {
      input = pattern.matcher(input).replaceAll("");
    }
    return input;
  }

  /**
   * Normalize any hex pattern to a standard hex pattern.
   *
   * @param hex the hex pattern to normalize
   * @return the normalized hex pattern
   */
  private static @NotNull String normalizeHex(@NotNull String hex) {
    if (hex.startsWith("<") || hex.startsWith("{")) {
      return hex.substring(1, hex.length() - 1);
    } else if (hex.startsWith("&")) {
      return hex.substring(1);
    } else {
      return hex;
    }
  }
}
