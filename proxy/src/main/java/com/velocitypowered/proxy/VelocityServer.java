/*
 * Copyright (C) 2018-2023 Velocity Contributors
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

package com.velocitypowered.proxy;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.util.Favicon;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.api.util.ProxyVersion;
import com.velocitypowered.proxy.command.VelocityCommandManager;
import com.velocitypowered.proxy.command.builtin.AlertCommand;
import com.velocitypowered.proxy.command.builtin.AlertRawCommand;
import com.velocitypowered.proxy.command.builtin.CallbackCommand;
import com.velocitypowered.proxy.command.builtin.FindCommand;
import com.velocitypowered.proxy.command.builtin.GlistCommand;
import com.velocitypowered.proxy.command.builtin.HubCommand;
import com.velocitypowered.proxy.command.builtin.PingCommand;
import com.velocitypowered.proxy.command.builtin.SendCommand;
import com.velocitypowered.proxy.command.builtin.ServerCommand;
import com.velocitypowered.proxy.command.builtin.ShowAllCommand;
import com.velocitypowered.proxy.command.builtin.ShutdownCommand;
import com.velocitypowered.proxy.command.builtin.VelocityCommand;
import com.velocitypowered.proxy.config.VelocityConfiguration;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.player.resourcepack.VelocityResourcePackInfo;
import com.velocitypowered.proxy.connection.util.ServerListPingHandler;
import com.velocitypowered.proxy.console.VelocityConsole;
import com.velocitypowered.proxy.crypto.EncryptionUtils;
import com.velocitypowered.proxy.event.VelocityEventManager;
import com.velocitypowered.proxy.network.ConnectionManager;
import com.velocitypowered.proxy.plugin.VelocityPluginManager;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.util.FaviconSerializer;
import com.velocitypowered.proxy.protocol.util.GameProfileSerializer;
import com.velocitypowered.proxy.scheduler.VelocityScheduler;
import com.velocitypowered.proxy.server.ServerMap;
import com.velocitypowered.proxy.util.AddressUtil;
import com.velocitypowered.proxy.util.ClosestLocaleMatcher;
import com.velocitypowered.proxy.util.ResourceUtils;
import com.velocitypowered.proxy.util.VelocityChannelRegistrar;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiter;
import com.velocitypowered.proxy.util.ratelimit.Ratelimiters;
import com.velocitypowered.proxy.util.translation.VelocityTranslationRegistry;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.translation.Translator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implementation of {@link ProxyServer}.
 */
public class VelocityServer implements ProxyServer, ForwardingAudience {

  private static final Logger logger = LogManager.getLogger(VelocityServer.class);
  public static final Gson GENERAL_GSON = new GsonBuilder()
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .registerTypeHierarchyAdapter(GameProfile.class, GameProfileSerializer.INSTANCE)
      .create();
  private static final Gson PRE_1_16_PING_SERIALIZER = new GsonBuilder()
      .registerTypeHierarchyAdapter(
          Component.class,
          ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_15_2)
                  .serializer().getAdapter(Component.class)
      )
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .create();
  private static final Gson PRE_1_20_3_PING_SERIALIZER = new GsonBuilder()
      .registerTypeHierarchyAdapter(
          Component.class,
          ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_20_2)
                  .serializer().getAdapter(Component.class)
      )
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .create();
  private static final Gson MODERN_PING_SERIALIZER = new GsonBuilder()
      .registerTypeHierarchyAdapter(
          Component.class,
          ProtocolUtils.getJsonChatSerializer(ProtocolVersion.MINECRAFT_1_20_3)
                  .serializer().getAdapter(Component.class)
      )
      .registerTypeHierarchyAdapter(Favicon.class, FaviconSerializer.INSTANCE)
      .create();

  private final ConnectionManager cm;
  private final ProxyOptions options;
  private @MonotonicNonNull VelocityConfiguration configuration;
  private @MonotonicNonNull KeyPair serverKeyPair;
  private final ServerMap servers;
  private final VelocityCommandManager commandManager;
  private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
  private boolean shutdown = false;
  private final VelocityPluginManager pluginManager;

  private final Map<UUID, ConnectedPlayer> connectionsByUuid = new ConcurrentHashMap<>();
  private final Map<String, ConnectedPlayer> connectionsByName = new ConcurrentHashMap<>();
  private final VelocityConsole console;
  private @MonotonicNonNull Ratelimiter ipAttemptLimiter;
  private final VelocityEventManager eventManager;
  private final VelocityScheduler scheduler;
  private final VelocityChannelRegistrar channelRegistrar = new VelocityChannelRegistrar();
  private final ServerListPingHandler serverListPingHandler;
  private final Key translationRegistryKey = Key.key("velocity", "translations");

  VelocityServer(final ProxyOptions options) {
    pluginManager = new VelocityPluginManager(this);
    eventManager = new VelocityEventManager(pluginManager);
    commandManager = new VelocityCommandManager(eventManager);
    scheduler = new VelocityScheduler(pluginManager);
    console = new VelocityConsole(this);
    cm = new ConnectionManager(this);
    servers = new ServerMap(this);
    serverListPingHandler = new ServerListPingHandler(this);
    this.options = options;
  }

  public KeyPair getServerKeyPair() {
    return serverKeyPair;
  }

  @Override
  public VelocityConfiguration getConfiguration() {
    return this.configuration;
  }

  @Override
  public ProxyVersion getVersion() {
    Package pkg = VelocityServer.class.getPackage();
    String implName;
    String implVersion;
    String implVendor;
    if (pkg != null) {
      implName = MoreObjects.firstNonNull(pkg.getImplementationTitle(), "Velocity");
      implVersion = MoreObjects.firstNonNull(pkg.getImplementationVersion(), "<unknown>");
      implVendor = MoreObjects.firstNonNull(pkg.getImplementationVendor(), "Velocity Contributors");
    } else {
      implName = "Velocity";
      implVersion = "<unknown>";
      implVendor = "Velocity Contributors";
    }

    return new ProxyVersion(implName, implVendor, implVersion);
  }

  @Override
  public VelocityCommandManager getCommandManager() {
    return commandManager;
  }

  void awaitProxyShutdown() {
    cm.getBossGroup().terminationFuture().syncUninterruptibly();
  }

  @EnsuresNonNull({"serverKeyPair", "servers", "pluginManager", "eventManager", "scheduler",
      "console", "cm", "configuration"})
  void start() {
    logger.info("Booting up {} {}...", getVersion().getName(), getVersion().getVersion());
    console.setupStreams();

    serverKeyPair = EncryptionUtils.createRsaKeyPair(1024);

    cm.logChannelInformation();

    this.doStartupConfigLoad();

    // Initialize commands first
    commandManager.register(VelocityCommand.create(this));
    commandManager.register(CallbackCommand.create());
    commandManager.register("shutdown", ShutdownCommand.command(this),
        "end", "stop");
    new AlertCommand(this).register(configuration.isAlertEnabled());
    new AlertRawCommand(this).register(configuration.isAlertRawEnabled());
    new FindCommand(this).register(configuration.isFindEnabled());
    new GlistCommand(this).register(configuration.isGlistEnabled());
    new PingCommand(this).register(configuration.isPingEnabled());
    new SendCommand(this).register(configuration.isSendEnabled());
    new ShowAllCommand(this).register(configuration.isShowAllEnabled());

    final BrigadierCommand serverCommand = ServerCommand.create(this, configuration.isServerEnabled());
    if (serverCommand != null) {
      commandManager.register(serverCommand);
    }

    if (configuration.isHubEnabled()) {
      commandManager.register("hub", new HubCommand(this).register(configuration.isHubEnabled()), "lobby");
    }

    registerTranslations(true);

    for (Map.Entry<String, String> entry : configuration.getServers().entrySet()) {
      servers.register(new ServerInfo(entry.getKey(), AddressUtil.parseAddress(entry.getValue())));
    }

    ipAttemptLimiter = Ratelimiters.createWithMilliseconds(configuration.getLoginRatelimit());
    loadPlugins();

    // Go ahead and fire the proxy initialization event. We block since plugins should have a chance
    // to fully initialize before we accept any connections to the server.
    eventManager.fire(new ProxyInitializeEvent()).join();

    // init console permissions after plugins are loaded
    console.setupPermissions();

    final Integer port = this.options.getPort();
    if (port != null) {
      logger.debug("Overriding bind port to {} from command line option", port);
      this.cm.bind(new InetSocketAddress(configuration.getBind().getHostString(), port));
    } else {
      this.cm.bind(configuration.getBind());
    }

    final Boolean haproxy = this.options.isHaproxy();
    if (haproxy != null) {
      logger.debug("Overriding HAProxy protocol to {} from command line option", haproxy);
      configuration.setProxyProtocol(haproxy);
    }

    if (configuration.isQueryEnabled()) {
      this.cm.queryBind(configuration.getBind().getHostString(), configuration.getQueryPort());
    }

    Metrics.VelocityMetrics.startMetrics(this, configuration.getMetrics());
  }

  private void unregisterTranslations() {
    for (final Translator source : GlobalTranslator.translator().sources()) {
      if (source.name().equals(this.translationRegistryKey)) {
        GlobalTranslator.translator().removeSource(source);
      }
    }
  }

  private void registerTranslations(boolean log) {
    final String defaultFile = "messages.properties";
    final TranslationRegistry translationRegistry = new VelocityTranslationRegistry(TranslationRegistry.create(this.translationRegistryKey));
    translationRegistry.defaultLocale(Locale.US);
    try {
      ResourceUtils.visitResources(VelocityServer.class, path -> {
        if (log) {
          logger.info("Loading localizations...");
        }

        final Path langPath = Path.of("lang");

        try (Stream<Path> files = Files.walk(path)) {
          if (!Files.exists(langPath)) {
            Files.createDirectory(langPath);

            files.filter(Files::isRegularFile).forEach(file -> {
              try {
                final Path langFile = langPath.resolve(file.getFileName().toString());
                if (!Files.exists(langFile)) {
                  try (final InputStream is = Files.newInputStream(file)) {
                    Files.copy(is, langFile);
                  }
                }
              } catch (IOException e) {
                logger.error("Encountered an I/O error whilst loading translations", e);
              }
            });
          }

          Optional<Path> optionalPath;
          try (Stream<Path> defaultFiles = Files.walk(path)) {
            optionalPath = defaultFiles.filter(temp -> temp.toString().endsWith(defaultFile)).findFirst();
          }

          if (optionalPath.isEmpty()) {
            logger.error("Encountered an error when attempting to read default translations)");
            return;
          }

          try (final BufferedReader defaultReader = Files.newBufferedReader(optionalPath.get(), StandardCharsets.UTF_8)) {
            final ResourceBundle defaultBundle = new PropertyResourceBundle(defaultReader);
            final Set<String> defaultKeys = defaultBundle.keySet();

            try (final Stream<Path> langFiles = Files.walk(langPath)) {
              langFiles.filter(Files::isRegularFile).forEach(file -> {
                final String filename = com.google.common.io.Files
                    .getNameWithoutExtension(file.getFileName().toString());
                final String localeName = filename.replace("messages_", "")
                    .replace("messages", "")
                    .replace('_', '-');
                final Locale locale = localeName.isBlank()
                    ? Locale.US
                    : Locale.forLanguageTag(localeName);

                try (final BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                  final ResourceBundle bundle = new PropertyResourceBundle(reader);

                  translationRegistry.registerAll(locale, defaultKeys, (key) -> {
                    final String format = bundle.containsKey(key) ? bundle.getString(key) : defaultBundle.getString(key);
                    final String escapedFormat = format.replace("'", "''");

                    return new MessageFormat(escapedFormat, locale);
                  });

                  ClosestLocaleMatcher.INSTANCE.registerKnown(locale);
                } catch (Exception e) {
                  logger.error("Could not read language file: {}", filename, e);
                }
              });
            } catch (Exception e) {
              logger.error("Failed to read directory: {}", path.toString(), e);
            }
          }
        } catch (Exception e) {
          logger.error("An unknown exception occurred.", e);
        }
      }, "com", "velocitypowered", "proxy", "l10n");
    } catch (IOException e) {
      logger.error("Encountered an I/O error whilst loading translations", e);
      return;
    }
    GlobalTranslator.translator().addSource(translationRegistry);
  }

  @SuppressFBWarnings("DM_EXIT")
  private void doStartupConfigLoad() {
    try {
      Path configPath = Path.of("velocity.toml");
      configuration = VelocityConfiguration.read(configPath);

      if (!configuration.validate()) {
        logger.error("Your configuration is invalid. Velocity will not start up until the errors "
            + "are resolved.");
        LogManager.shutdown();
        System.exit(1);
      }

      commandManager.setAnnounceProxyCommands(configuration.isAnnounceProxyCommands());
    } catch (Exception e) {
      logger.error("Unable to read/load/save your velocity.toml. The server will shut down.", e);
      LogManager.shutdown();
      System.exit(1);
    }
  }

  private void loadPlugins() {
    logger.info("Loading plugins...");

    try {
      Path pluginPath = Path.of("plugins");

      if (!pluginPath.toFile().exists()) {
        Files.createDirectory(pluginPath);
      } else {
        if (!pluginPath.toFile().isDirectory()) {
          logger.warn("Plugin location {} is not a directory, continuing without loading plugins",
              pluginPath);
          return;
        }

        pluginManager.loadPlugins(pluginPath);
      }
    } catch (Exception e) {
      logger.error("Couldn't load plugins", e);
    }

    // Register the plugin main classes so that we can fire the proxy initialize event
    for (PluginContainer plugin : pluginManager.getPlugins()) {
      Optional<?> instance = plugin.getInstance();
      if (instance.isPresent()) {
        try {
          eventManager.registerInternally(plugin, instance.get());
        } catch (Exception e) {
          logger.error("Unable to register plugin listener for {}",
              plugin.getDescription().getName().orElse(plugin.getDescription().getId()), e);
        }
      }
    }

    logger.info("Loaded {} plugins", pluginManager.getPlugins().size());
  }

  public Bootstrap createBootstrap(@Nullable EventLoopGroup group) {
    return this.cm.createWorker(group);
  }

  public ChannelInitializer<Channel> getBackendChannelInitializer() {
    return this.cm.backendChannelInitializer.get();
  }

  public ServerListPingHandler getServerListPingHandler() {
    return serverListPingHandler;
  }

  public boolean isShutdown() {
    return shutdown;
  }

  /**
   * Reloads the proxy's configuration.
   *
   * @return {@code true} if successful, {@code false} if we can't read the configuration
   * @throws IOException if we can't read {@code velocity.toml}
   */
  public boolean reloadConfiguration() throws IOException {
    Path configPath = Path.of("velocity.toml");
    VelocityConfiguration newConfiguration = VelocityConfiguration.read(configPath);

    if (!newConfiguration.validate()) {
      return false;
    }

    unregisterCommands();

    this.configuration = newConfiguration;

    registerCommands();

    unregisterTranslations();

    registerTranslations(false);

    // Re-register servers. If a server is being replaced, make sure to note what players need to
    // move back to a fallback server.
    Collection<ConnectedPlayer> evacuate = new ArrayList<>();
    for (Map.Entry<String, String> entry : newConfiguration.getServers().entrySet()) {
      ServerInfo newInfo = new ServerInfo(entry.getKey(), AddressUtil.parseAddress(entry.getValue()));
      Optional<RegisteredServer> rs = servers.getServer(entry.getKey());
      if (rs.isEmpty()) {
        servers.register(newInfo);
      } else if (!rs.get().getServerInfo().equals(newInfo)) {
        for (Player player : rs.get().getPlayersConnected()) {
          if (!(player instanceof ConnectedPlayer)) {
            throw new IllegalStateException("ConnectedPlayer not found for player " + player
                + " in server " + rs.get().getServerInfo().getName());
          }
          evacuate.add((ConnectedPlayer) player);
        }
        servers.unregister(rs.get().getServerInfo());
        servers.register(newInfo);
      }
    }

    // If we had any players to evacuate, let's move them now. Wait until they are all moved off.
    if (!evacuate.isEmpty()) {
      CountDownLatch latch = new CountDownLatch(evacuate.size());
      for (ConnectedPlayer player : evacuate) {
        Optional<RegisteredServer> next = player.getNextServerToTry();
        if (next.isPresent()) {
          player.createConnectionRequest(next.get()).connectWithIndication()
              .whenComplete((success, ex) -> {
                if (ex != null || success == null || !success) {
                  player.disconnect(Component.text("Your server has been changed, but we could "
                      + "not move you to any fallback servers."));
                }
                latch.countDown();
              });
        } else {
          latch.countDown();
          player.disconnect(Component.text("Your server has been changed, but we could "
              + "not move you to any fallback servers."));
        }
      }
      try {
        latch.await();
      } catch (InterruptedException e) {
        logger.error("Interrupted whilst moving players", e);
        Thread.currentThread().interrupt();
      }
    }

    // If we have a new bind address, bind to it
    if (!configuration.getBind().equals(newConfiguration.getBind())) {
      this.cm.bind(newConfiguration.getBind());
      this.cm.close(configuration.getBind());
    }

    boolean queryPortChanged = newConfiguration.getQueryPort() != configuration.getQueryPort();
    boolean queryAlreadyEnabled = configuration.isQueryEnabled();
    boolean queryEnabled = newConfiguration.isQueryEnabled();
    if (queryAlreadyEnabled && (!queryEnabled || queryPortChanged)) {
      this.cm.close(new InetSocketAddress(
          configuration.getBind().getHostString(), configuration.getQueryPort()));
    }
    if (queryEnabled && (!queryAlreadyEnabled || queryPortChanged)) {
      this.cm.queryBind(newConfiguration.getBind().getHostString(),
          newConfiguration.getQueryPort());
    }

    commandManager.setAnnounceProxyCommands(newConfiguration.isAnnounceProxyCommands());
    ipAttemptLimiter = Ratelimiters.createWithMilliseconds(newConfiguration.getLoginRatelimit());
    this.configuration = newConfiguration;
    eventManager.fireAndForget(new ProxyReloadEvent());
    return true;
  }

  private void unregisterCommands() {
    commandManager.unregister("server");
    commandManager.unregister("alert");
    commandManager.unregister("alertraw");
    commandManager.unregister("find");
    commandManager.unregister("glist");
    commandManager.unregister("ping");
    commandManager.unregister("send");
    commandManager.unregister("showall");
    commandManager.unregister("hub");
    commandManager.unregister("lobby");
  }

  private void registerCommands() {
    new AlertCommand(this).register(configuration.isAlertEnabled());
    new AlertRawCommand(this).register(configuration.isAlertRawEnabled());
    new FindCommand(this).register(configuration.isFindEnabled());
    new GlistCommand(this).register(configuration.isGlistEnabled());
    new PingCommand(this).register(configuration.isPingEnabled());
    new SendCommand(this).register(configuration.isSendEnabled());
    new ShowAllCommand(this).register(configuration.isShowAllEnabled());

    final BrigadierCommand serverCommand = ServerCommand.create(this, configuration.isServerEnabled());
    if (serverCommand != null) {
      commandManager.register(serverCommand);
    }

    if (configuration.isHubEnabled()) {
      commandManager.register("hub", new HubCommand(this).register(configuration.isHubEnabled()), "lobby");
    }
  }

  /**
   * Shuts down the proxy, kicking players with the specified reason.
   *
   * @param explicitExit whether the user explicitly shut down the proxy
   * @param reason       message to kick online players with
   */
  public void shutdown(boolean explicitExit, Component reason) {
    if (eventManager == null || pluginManager == null || cm == null || scheduler == null) {
      throw new AssertionError();
    }

    if (!shutdownInProgress.compareAndSet(false, true)) {
      return;
    }

    Runnable shutdownProcess = () -> {
      logger.info("Shutting down the proxy...");

      // Shutdown the connection manager, this should be
      // done first to refuse new connections
      cm.shutdown();

      ImmutableList<ConnectedPlayer> players = ImmutableList.copyOf(connectionsByUuid.values());
      for (ConnectedPlayer player : players) {
        player.disconnect(reason);
      }

      try {
        boolean timedOut = false;

        try {
          // Wait for the connections finish tearing down, this
          // makes sure that all the disconnect events are being fired

          CompletableFuture<Void> playersTeardownFuture = CompletableFuture.allOf(players.stream()
                  .map(ConnectedPlayer::getTeardownFuture)
                  .toArray((IntFunction<CompletableFuture<Void>[]>) CompletableFuture[]::new));

          playersTeardownFuture.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
          timedOut = true;
        } catch (ExecutionException e) {
          timedOut = true;
          logger.error("Exception while tearing down player connections", e);
        }

        eventManager.fire(new ProxyShutdownEvent()).join();

        timedOut = !eventManager.shutdown() || timedOut;
        timedOut = !scheduler.shutdown() || timedOut;

        if (timedOut) {
          logger.error("Your plugins took over 10 seconds to shut down.");
        }
      } catch (InterruptedException e) {
        // Not much we can do about this...
        Thread.currentThread().interrupt();
      }

      // Since we manually removed the shutdown hook, we need to handle the shutdown ourselves.
      LogManager.shutdown();

      shutdown = true;

      if (explicitExit) {
        System.exit(0);
      }
    };

    if (explicitExit) {
      Thread thread = new Thread(shutdownProcess);
      thread.start();
    } else {
      shutdownProcess.run();
    }
  }

  /**
   * Calls {@link #shutdown(boolean, Component)} with the default reason "Proxy shutting down.".
   *
   * @param explicitExit whether the user explicitly shut down the proxy
   */
  public void shutdown(boolean explicitExit) {
    shutdown(explicitExit, Component.translatable("velocity.kick.shutdown"));
  }

  @Override
  public void shutdown(Component reason) {
    shutdown(true, reason);
  }

  @Override
  public void shutdown() {
    shutdown(true);
  }

  @Override
  public void closeListeners() {
    this.cm.closeEndpoints(false);
  }

  public HttpClient createHttpClient() {
    return cm.createHttpClient();
  }

  public Ratelimiter getIpAttemptLimiter() {
    return ipAttemptLimiter;
  }

  /**
   * Checks if the {@code connection} can be registered with the proxy.
   *
   * @param connection the connection to check
   * @return {@code true} if we can register the connection, {@code false} if not
   */
  public boolean canRegisterConnection(ConnectedPlayer connection) {
    if (configuration.isOnlineMode() && configuration.isOnlineModeKickExistingPlayers()) {
      return true;
    }
    String lowerName = connection.getUsername().toLowerCase(Locale.US);
    return !(connectionsByName.containsKey(lowerName)
        || connectionsByUuid.containsKey(connection.getUniqueId()));
  }

  /**
   * Attempts to register the {@code connection} with the proxy.
   *
   * @param connection the connection to register
   * @return {@code true} if we registered the connection, {@code false} if not
   */
  public boolean registerConnection(ConnectedPlayer connection) {
    String lowerName = connection.getUsername().toLowerCase(Locale.US);

    if (!this.configuration.isOnlineModeKickExistingPlayers()) {
      if (connectionsByName.putIfAbsent(lowerName, connection) != null) {
        return false;
      }
      if (connectionsByUuid.putIfAbsent(connection.getUniqueId(), connection) != null) {
        connectionsByName.remove(lowerName, connection);
        return false;
      }
    } else {
      ConnectedPlayer existing = connectionsByUuid.get(connection.getUniqueId());
      if (existing != null) {
        existing.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"));
      }

      // We can now replace the entries as needed.
      connectionsByName.put(lowerName, connection);
      connectionsByUuid.put(connection.getUniqueId(), connection);
    }
    return true;
  }

  /**
   * Unregisters the given player from the proxy.
   *
   * @param connection the connection to unregister
   */
  public void unregisterConnection(ConnectedPlayer connection) {
    connectionsByName.remove(connection.getUsername().toLowerCase(Locale.US), connection);
    connectionsByUuid.remove(connection.getUniqueId(), connection);
    connection.disconnected();
  }

  @Override
  public Optional<Player> getPlayer(String username) {
    Preconditions.checkNotNull(username, "username");
    return Optional.ofNullable(connectionsByName.get(username.toLowerCase(Locale.US)));
  }

  @Override
  public Optional<Player> getPlayer(UUID uuid) {
    Preconditions.checkNotNull(uuid, "uuid");
    return Optional.ofNullable(connectionsByUuid.get(uuid));
  }

  @Override
  public Collection<Player> matchPlayer(String partialName) {
    Objects.requireNonNull(partialName);

    return getAllPlayers().stream().filter(p -> p.getUsername()
            .regionMatches(true, 0, partialName, 0, partialName.length()))
        .collect(Collectors.toList());
  }

  @Override
  public Collection<RegisteredServer> matchServer(String partialName) {
    Objects.requireNonNull(partialName);

    return getAllServers().stream().filter(s -> s.getServerInfo().getName()
            .regionMatches(true, 0, partialName, 0, partialName.length()))
        .collect(Collectors.toList());
  }

  @Override
  public Collection<Player> getAllPlayers() {
    return ImmutableList.copyOf(connectionsByUuid.values());
  }

  @Override
  public int getPlayerCount() {
    return connectionsByUuid.size();
  }

  @Override
  public Optional<RegisteredServer> getServer(String name) {
    return servers.getServer(name);
  }

  @Override
  public Collection<RegisteredServer> getAllServers() {
    return servers.getAllServers();
  }

  @Override
  public RegisteredServer createRawRegisteredServer(ServerInfo server) {
    return servers.createRawRegisteredServer(server);
  }

  @Override
  public RegisteredServer registerServer(ServerInfo server) {
    return servers.register(server);
  }

  @Override
  public void unregisterServer(ServerInfo server) {
    servers.unregister(server);
  }

  @Override
  public VelocityConsole getConsoleCommandSource() {
    return console;
  }

  @Override
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  @Override
  public VelocityEventManager getEventManager() {
    return eventManager;
  }

  @Override
  public VelocityScheduler getScheduler() {
    return scheduler;
  }

  @Override
  public VelocityChannelRegistrar getChannelRegistrar() {
    return channelRegistrar;
  }

  @Override
  public InetSocketAddress getBoundAddress() {
    if (configuration == null) {
      throw new IllegalStateException(
          "No configuration"); // even though you'll never get the chance... heh, heh
    }
    return configuration.getBind();
  }

  @Override
  public @NonNull Iterable<? extends Audience> audiences() {
    Collection<Audience> audiences = new ArrayList<>(this.getPlayerCount() + 1);
    audiences.add(this.console);
    audiences.addAll(this.getAllPlayers());
    return audiences;
  }

  /**
   * Returns a Gson instance for use in serializing server ping instances.
   *
   * @param version the protocol version in use
   * @return the Gson instance
   */
  public static Gson getPingGsonInstance(ProtocolVersion version) {
    if (version == ProtocolVersion.UNKNOWN
        || version.noLessThan(ProtocolVersion.MINECRAFT_1_20_3)) {
      return MODERN_PING_SERIALIZER;
    }
    if (version.noLessThan(ProtocolVersion.MINECRAFT_1_16)) {
      return PRE_1_20_3_PING_SERIALIZER;
    }
    return PRE_1_16_PING_SERIALIZER;
  }

  @Override
  public ResourcePackInfo.Builder createResourcePackBuilder(String url) {
    return new VelocityResourcePackInfo.BuilderImpl(url);
  }
}
