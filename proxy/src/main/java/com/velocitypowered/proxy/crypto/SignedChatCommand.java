/*
 * Copyright (C) 2022-2023 Velocity Contributors
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

package com.velocitypowered.proxy.crypto;

import com.google.common.base.Preconditions;
import com.velocitypowered.api.proxy.crypto.KeySigned;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a signed chat command.
 */
public class SignedChatCommand implements KeySigned {

  private final String command;
  private final PublicKey signer;
  private final Instant expiry;
  private final byte[] salt;
  public final UUID sender;
  private final boolean isPreviewSigned;

  private final Map<String, byte[]> signatures;
  private final SignaturePair[] previousSignatures;
  private final @Nullable SignaturePair lastSignature;


  /**
   * Create a signed command from data.
   */
  public SignedChatCommand(final String command, final PublicKey signer, final UUID sender,
      final Instant expiry, final Map<String, byte[]> signature, final byte[] salt,
      final boolean isPreviewSigned, final SignaturePair[] previousSignatures,
      @Nullable final SignaturePair lastSignature) {
    this.command = Preconditions.checkNotNull(command);
    this.signer = Preconditions.checkNotNull(signer);
    this.sender = Preconditions.checkNotNull(sender);
    this.signatures = Preconditions.checkNotNull(signature);
    this.expiry = Preconditions.checkNotNull(expiry);
    this.salt = Preconditions.checkNotNull(salt);
    this.isPreviewSigned = isPreviewSigned;
    this.previousSignatures = previousSignatures;
    this.lastSignature = lastSignature;

  }

  @Override
  public PublicKey getSigner() {
    return signer;
  }

  @Override
  public Instant getExpiryTemporal() {
    return expiry;
  }

  @Override
  public byte @Nullable [] getSignature() {
    return null;
  }

  @Override
  public byte[] getSalt() {
    return salt;
  }

  public String getBaseCommand() {
    return command;
  }


  public Map<String, byte[]> getSignatures() {
    return signatures;
  }

  public boolean isPreviewSigned() {
    return isPreviewSigned;
  }

  public @Nullable SignaturePair getLastSignature() {
    return lastSignature;
  }

  public SignaturePair[] getPreviousSignatures() {
    return previousSignatures;
  }
}
