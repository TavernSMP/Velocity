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

import com.google.common.base.Preconditions;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A builder for creating {@link VelocityArgumentCommandNode}s.
 *
 * @param <S> the type of the command source
 * @param <T> the type of the argument to parse
 */
public final class VelocityArgumentBuilder<S, T>
    extends ArgumentBuilder<S, VelocityArgumentBuilder<S, T>> {

  /**
   * Creates a builder for creating {@link VelocityArgumentCommandNode}s with the given name and
   * type.
   *
   * @param name the name of the node
   * @param type the type of the argument to parse
   * @param <S>  the type of the command source
   * @param <T>  the type of the argument to parse
   * @return a builder
   */
  public static <S, T> VelocityArgumentBuilder<S, T> velocityArgument(final String name,
      final ArgumentType<T> type) {
    Preconditions.checkNotNull(name, "name");
    Preconditions.checkNotNull(type, "type");
    return new VelocityArgumentBuilder<>(name, type);
  }

  /**
   * The name of the argument.
   *
   * <p>This field stores the name of the argument being processed or referenced. It is used to
   * identify the argument within commands or functions.</p>
   */
  private final String name;

  /**
   * The type of the argument.
   *
   * <p>This field represents the {@link ArgumentType} of the argument, defining the expected
   * data type or structure for the argument (e.g., string, integer, etc.).</p>
   * <@T> the type of the argument
   */
  private final ArgumentType<T> type;

  /**
   * A provider for auto-completion or suggestions.
   *
   * <p>This field holds an optional {@link SuggestionProvider} used to offer dynamic suggestions
   * to the user when they are entering a command. It may be {@code null} if no suggestions are provided.</p>
   * <@S> the type used by the suggestion provider (typically the source of the command)
   */
  private SuggestionProvider<S> suggestionsProvider = null;

  private VelocityArgumentBuilder(final String name, final ArgumentType<T> type) {
    this.name = name;
    this.type = type;
  }

  /**
   * Sets the {@link SuggestionProvider} for this argument builder to provide auto-completion suggestions.
   *
   * <p>This method allows the specification of a {@code SuggestionProvider}, which is used to generate
   * dynamic suggestions for the command argument when the user is typing.</p>
   *
   * @param provider the {@link SuggestionProvider} that will supply suggestions, or {@code null} if no suggestions are provided
   * @return the current {@link VelocityArgumentBuilder} instance for method chaining
   */
  public VelocityArgumentBuilder<S, T> suggests(final @Nullable SuggestionProvider<S> provider) {
    this.suggestionsProvider = provider;
    return this;
  }

  @Override
  public VelocityArgumentBuilder<S, T> then(final ArgumentBuilder<S, ?> argument) {
    throw new UnsupportedOperationException("Cannot add children to a greedy node");
  }

  @Override
  public VelocityArgumentBuilder<S, T> then(final CommandNode<S> argument) {
    throw new UnsupportedOperationException("Cannot add children to a greedy node");
  }

  @Override
  protected VelocityArgumentBuilder<S, T> getThis() {
    return this;
  }

  @Override
  public VelocityArgumentCommandNode<S, T> build() {
    return new VelocityArgumentCommandNode<>(this.name, this.type, getCommand(), getRequirement(),
        getContextRequirement(), getRedirect(), getRedirectModifier(), isFork(),
        this.suggestionsProvider);
  }
}
