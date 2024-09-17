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

package com.velocitypowered.proxy.protocol.packet.brigadier;

import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import io.netty.buffer.ByteBuf;

public final class RegistryKeyArgumentList {

  public static class ResourceOrTag extends RegistryKeyArgument {

    public ResourceOrTag(final String identifier) {
      super(identifier);
    }

    public static class Serializer implements ArgumentPropertySerializer<ResourceOrTag> {

      static final ResourceOrTag.Serializer REGISTRY = new ResourceOrTag.Serializer();

      @Override
      public ResourceOrTag deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
        return new ResourceOrTag(ProtocolUtils.readString(buf));
      }

      @Override
      public void serialize(final ResourceOrTag object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
        ProtocolUtils.writeString(buf, object.getIdentifier());
      }
    }
  }

  public static class ResourceOrTagKey extends RegistryKeyArgument {

    public ResourceOrTagKey(final String identifier) {
      super(identifier);
    }

    public static class Serializer implements ArgumentPropertySerializer<ResourceOrTagKey> {

      static final ResourceOrTagKey.Serializer REGISTRY = new ResourceOrTagKey.Serializer();

      @Override
      public ResourceOrTagKey deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
        return new ResourceOrTagKey(ProtocolUtils.readString(buf));
      }

      @Override
      public void serialize(final ResourceOrTagKey object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
        ProtocolUtils.writeString(buf, object.getIdentifier());
      }
    }
  }

  public static class Resource extends RegistryKeyArgument {

    public Resource(final String identifier) {
      super(identifier);
    }

    public static class Serializer implements ArgumentPropertySerializer<Resource> {

      static final Resource.Serializer REGISTRY = new Resource.Serializer();

      @Override
      public Resource deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
        return new Resource(ProtocolUtils.readString(buf));
      }

      @Override
      public void serialize(final Resource object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
        ProtocolUtils.writeString(buf, object.getIdentifier());
      }
    }
  }

  public static class ResourceKey extends RegistryKeyArgument {

    public ResourceKey(final String identifier) {
      super(identifier);
    }

    public static class Serializer implements ArgumentPropertySerializer<ResourceKey> {

      static final ResourceKey.Serializer REGISTRY = new ResourceKey.Serializer();

      @Override
      public ResourceKey deserialize(final ByteBuf buf, final ProtocolVersion protocolVersion) {
        return new ResourceKey(ProtocolUtils.readString(buf));
      }

      @Override
      public void serialize(final ResourceKey object, final ByteBuf buf, final ProtocolVersion protocolVersion) {
        ProtocolUtils.writeString(buf, object.getIdentifier());
      }
    }
  }

  RegistryKeyArgumentList() {
  }
}
