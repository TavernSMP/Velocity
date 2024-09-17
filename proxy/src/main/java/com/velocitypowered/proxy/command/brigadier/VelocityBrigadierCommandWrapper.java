/*
 * Copyright (C) 2024 Velocity Contributors
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

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.velocitypowered.api.command.CommandSource;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Wraps a Brigadier command to allow us to track the registrant.
 */
public final class VelocityBrigadierCommandWrapper implements Command<CommandSource> {

  /**
   * The command delegate that performs the actual execution logic.
   *
   * <p>This field holds a reference to a {@link Command} that is responsible for executing
   * the command's logic. The delegate allows this class to delegate command execution
   * to the specified {@link Command} instance.</p>
   * <@CommandSource> the type of the source that executes the command
   */
  private final Command<CommandSource> delegate;

  /**
   * The object responsible for registering this command or component.
   *
   * <p>This field holds a reference to the entity (often a class or plugin) that registered
   * this command or component. It serves as a way to track or associate the registration
   * with the originating object.</p>
   */
  private final Object registrant;

  private VelocityBrigadierCommandWrapper(final Command<CommandSource> delegate, final Object registrant) {
    this.delegate = delegate;
    this.registrant = registrant;
  }

  /**
   * Transforms the given command into a {@code VelocityBrigadierCommandWrapper} if the registrant
   * is not null and if the command is not already wrapped.
   *
   * @param delegate the command to wrap
   * @param registrant the registrant of the command
   * @return the wrapped command, if necessary
   */
  public static Command<CommandSource> wrap(final Command<CommandSource> delegate, @Nullable final Object registrant) {
    if (registrant == null) {
      // nothing to wrap
      return delegate;
    }
    if (delegate instanceof VelocityBrigadierCommandWrapper) {
      // already wrapped
      return delegate;
    }
    return new VelocityBrigadierCommandWrapper(delegate, registrant);
  }

  @Override
  public int run(final CommandContext<CommandSource> context) throws CommandSyntaxException {
    return delegate.run(context);
  }

  /**
   * Retrieves the object that registered this command or component.
   *
   * <p>This method returns the {@code registrant}, which is the entity responsible
   * for registering this command or component. It provides access to the original
   * object that initiated the registration.</p>
   *
   * @return the object that registered this command or component
   */
  public Object registrant() {
    return registrant;
  }
}
