/*
 * Copyright (C) 2021-2023 Velocity Contributors
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

package com.velocitypowered.proxy.command.brigadier;

import com.google.common.base.Splitter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * An argument type that parses the remaining contents of a {@link StringReader}, splitting the
 * input into words and placing the results in a string array.
 */
public final class StringArrayArgumentType implements ArgumentType<String[]> {

  /**
   * A singleton instance of {@link StringArrayArgumentType}.
   *
   * <p>This provides a single shared instance of the {@link StringArrayArgumentType} class
   * that can be reused throughout the code to avoid unnecessary instantiations.</p>
   */
  public static final StringArrayArgumentType INSTANCE = new StringArrayArgumentType();

  /**
   * An empty {@code String} array.
   *
   * <p>This constant represents an immutable, zero-length {@code String} array, which can be
   * used as a default or placeholder where an empty array is required without allocating
   * new memory for each instance.</p>
   */
  public static final String[] EMPTY = new String[0];

  /**
   * A {@link Splitter} instance for splitting strings by words.
   *
   * <p>This constant is used to split input strings based on a specific delimiter or pattern,
   * typically spaces or other word boundaries, into individual words or components.</p>
   *
   * <p>It is defined as {@code static final} to ensure the splitter is reused, improving performance by
   * avoiding the creation of new splitter instances.</p>
   */
  private static final Splitter WORD_SPLITTER =
      Splitter.on(CommandDispatcher.ARGUMENT_SEPARATOR_CHAR);

  /**
   * A list of example strings used for reference or testing.
   *
   * <p>This constant provides a predefined {@link List} of example strings, which can be used
   * in various contexts such as documentation, testing, or validation scenarios.</p>
   *
   * <p>The list contains sample entries like {@code "word"} and {@code "some words"} to demonstrate
   * typical input values.</p>
   */
  private static final List<String> EXAMPLES = Arrays.asList("word", "some words");

  private StringArrayArgumentType() {
  }

  @Override
  public String[] parse(final StringReader reader) throws CommandSyntaxException {
    final String text = reader.getRemaining();
    reader.setCursor(reader.getTotalLength());
    if (text.isEmpty()) {
      return EMPTY;
    }
    return WORD_SPLITTER.splitToList(text).toArray(EMPTY);
  }

  @Override
  public String toString() {
    return "stringArray()";
  }

  @Override
  public Collection<String> getExamples() {
    return EXAMPLES;
  }
}
