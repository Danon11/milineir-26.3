package org.millenaire.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.millenaire.content.legacy.ConversionMode;
import org.millenaire.content.legacy.ConverterOutputManifest;
import org.millenaire.content.legacy.LegacyConversionDriver;
import org.millenaire.content.legacy.LegacyConversionReport;
import org.millenaire.content.legacy.LegacyLayoutDetector;
import org.slf4j.Logger;

public final class ConvertAddonCommand {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "millenaire-convert-addon");
      t.setDaemon(true);
      return t;
   });
   private static final AtomicReference<CompletableFuture<?>> running = new AtomicReference<>(null);

   private ConvertAddonCommand() {
   }

   public static void registerUnder(LiteralArgumentBuilder<CommandSourceStack> dev) {
      dev.then(Commands.literal("convert-addon").then(Commands.argument("path", StringArgumentType.greedyString()).executes(ConvertAddonCommand::run)));
   }

   public static void onServerStopping(ServerStoppingEvent event) {
      EXECUTOR.shutdown();

      try {
         if (!EXECUTOR.awaitTermination(10L, TimeUnit.SECONDS)) {
            LOGGER.warn("convert-addon worker did not finish within 10s; requesting interrupt");
            EXECUTOR.shutdownNow();
         }
      } catch (InterruptedException e) {
         EXECUTOR.shutdownNow();
         Thread.currentThread().interrupt();
      }
   }

   private static int run(CommandContext<CommandSourceStack> ctx) {
      CommandSourceStack src = (CommandSourceStack)ctx.getSource();
      String arg = StringArgumentType.getString(ctx, "path");
      return runImpl(src, arg);
   }

   static int runImpl(CommandSourceStack src, String pathArg) {
      if (!src.getServer().isDedicatedServer()) {
         src.sendFailure(Component.literal("convert-addon: not available on a single-player (integrated) server. Run a dedicated dev server."));
         return 0;
      } else {
         Path addonRoot = resolveAddonRoot(src, pathArg);
         if (addonRoot == null) {
            return 0;
         } else {
            CompletableFuture<?> current = running.get();
            if (current != null && !current.isDone()) {
               src.sendFailure(Component.literal("convert-addon: another conversion is already in progress. Wait for it to finish."));
               return 0;
            } else {
               launch(src, addonRoot);
               return 1;
            }
         }
      }
   }

   private static void launch(CommandSourceStack src, Path addonRoot) {
      CompletableFuture<ConvertAddonCommand.Result> future = CompletableFuture.supplyAsync(() -> runConversionOnWorker(addonRoot), EXECUTOR);
      running.set(future);
      src.sendSuccess(() -> Component.literal("convert-addon started on " + addonRoot + " — running in background..."), false);
      future.whenComplete((result, ex) -> {
         try {
            src.getServer().execute(() -> publishResult(src, result, ex));
         } catch (Throwable dispatchFailure) {
            LOGGER.info("convert-addon finished during shutdown on {}; result on disk only, no chat delivered.", addonRoot);
         }

         running.compareAndSet(future, null);
      });
   }

   static ConvertAddonCommand.Result runConversionOnWorker(Path addonRoot) {
      Path outputRoot = addonRoot.resolveSibling(addonRoot.getFileName() + "_converted");
      Path manifestPath = outputRoot.resolve("_conversion_manifest.json");
      if (Files.isRegularFile(manifestPath)) {
         String recordedVersion = ConverterOutputManifest.readVersion(manifestPath);
         return recordedVersion != null && !recordedVersion.equals("9.0.0-dev-preview.5")
            ? new ConvertAddonCommand.Result.VersionDrift(addonRoot, outputRoot, recordedVersion, "9.0.0-dev-preview.5")
            : new ConvertAddonCommand.Result.AlreadyConverted(addonRoot, outputRoot);
      }

      LegacyLayoutDetector.Detection detection = LegacyLayoutDetector.scan(addonRoot);
      if (detection.isEmpty()) {
         return new ConvertAddonCommand.Result.EmptyPack(addonRoot);
      }

      Path probeRoot = outputRoot.getParent() != null ? outputRoot.getParent() : addonRoot;
      if (!LegacyConversionDriver.isWritable(probeRoot)) {
         return new ConvertAddonCommand.Result.NotWritable(probeRoot);
      }

      try (LegacyConversionDriver.LockHandle handle = LegacyConversionDriver.acquireLockAt(addonRoot)) {
         if (!handle.isHeld()) {
            return new ConvertAddonCommand.Result.LockContention(handle.lockPath());
         }

         LegacyConversionDriver.DriverResult r = LegacyConversionDriver.runWithOutput(addonRoot, outputRoot, detection, ConversionMode.CONVERT);
         return new ConvertAddonCommand.Result.Completed(r);
      } catch (IOException e) {
         LOGGER.error("convert-addon I/O error on {}: {}", new Object[]{addonRoot, e.getMessage(), e});
         return new ConvertAddonCommand.Result.NotWritable(addonRoot);
      }
   }

   private static void publishResult(CommandSourceStack src, ConvertAddonCommand.Result result, Throwable ex) {
      try {
         if (src.getServer().isStopped() || src.getServer().isShutdown()) {
            LOGGER.info("convert-addon completion skipped: server stopped/shut down");
            return;
         }

         if (ex != null) {
            src.sendFailure(Component.literal("convert-addon crashed: " + ex.getClass().getSimpleName() + ". See server log."));
            LOGGER.error("convert-addon future completed exceptionally", ex);
            return;
         }

         renderResultLines(src, result);
      } catch (Throwable var6) {
         Throwable deliveryFailure = var6;

         try {
            LOGGER.warn("convert-addon result could not be delivered: {}", deliveryFailure.getMessage());
         } catch (RuntimeException var5) {
         }
      }
   }

   private static void renderResultLines(CommandSourceStack src, ConvertAddonCommand.Result result) {
      switch (result) {
         case ConvertAddonCommand.Result.EmptyPack ep:
            src.sendSuccess(() -> Component.literal("convert-addon: no legacy content detected at " + ep.addonRoot() + "."), false);
            break;
         case ConvertAddonCommand.Result.NotWritable nw:
            src.sendFailure(Component.literal("convert-addon: " + nw.addonRoot() + " is not writable. Fix permissions and retry."));
            break;
         case ConvertAddonCommand.Result.LockContention lc:
            src.sendFailure(Component.literal("convert-addon: another convert pass holds the lock at " + lc.lockFile() + ". Wait and retry."));
            break;
         case ConvertAddonCommand.Result.AlreadyConverted ac:
            src.sendSuccess(() -> Component.literal("convert-addon: " + ac.outputRoot() + " already exists — skipping. Delete it to re-convert."), false);
            break;
         case ConvertAddonCommand.Result.VersionDrift vd:
            src.sendFailure(
               Component.literal(
                  "convert-addon: "
                     + vd.outputRoot()
                     + " was produced by Millénaire "
                     + vd.recordedVersion()
                     + " but the running converter is "
                     + vd.runningVersion()
                     + ". Delete the directory and re-run to rebuild with the current format."
               )
            );
            break;
         case ConvertAddonCommand.Result.Completed c:
            LegacyConversionReport report = c.driver().report();
            int n = report.totalConverted();
            long ms = c.driver().elapsedMillis();
            if (report.hasBlockingAmbiguities()) {
               int k = (int)report.skipped().stream().filter(s -> s.fixHint() != null).count();
               src.sendFailure(
                  Component.literal(
                     String.format(
                        "convert-addon: %d converted, %d blocking ambiguities in %d ms. See _conversion_report.txt and _conversion_unmapped.*", n, k, ms
                     )
                  )
               );
            } else {
               src.sendSuccess(() -> Component.literal(String.format("convert-addon: %d files converted in %d ms. Clean — ready to ship.", n, ms)), false);
            }
            break;
         default:
            throw new MatchException(null, null);
      }
   }

   static Path resolveAddonRoot(CommandSourceStack src, String arg) {
      Path candidate = Path.of(arg);
      if (!candidate.isAbsolute()) {
         candidate = src.getServer().getServerDirectory().resolve(candidate);
      }

      Path serverRoot = src.getServer().getServerDirectory();
      ConvertAddonCommand.AddonRootCheck result = checkAddonRoot(candidate, serverRoot, isWindows());
      if (result instanceof ConvertAddonCommand.AddonRootCheck.Fail fail) {
         src.sendFailure(Component.literal("convert-addon: " + fail.reason()));
         return null;
      } else {
         return ((ConvertAddonCommand.AddonRootCheck.Ok)result).root();
      }
   }

   static ConvertAddonCommand.AddonRootCheck checkAddonRoot(Path candidate, Path serverRoot, boolean isWindows) {
      Path realAddon;
      Path realServer;
      try {
         realAddon = candidate.toRealPath();
         realServer = serverRoot.toRealPath();
      } catch (IOException e) {
         return new ConvertAddonCommand.AddonRootCheck.Fail("path does not exist or cannot be canonicalised: " + candidate);
      }

      if (!Files.isDirectory(realAddon)) {
         return new ConvertAddonCommand.AddonRootCheck.Fail("not a directory: " + realAddon);
      }

      boolean contained;
      if (isWindows) {
         String sep = File.separator;
         String addonLower = realAddon.toString().toLowerCase(Locale.ROOT) + sep;
         String serverLower = realServer.toString().toLowerCase(Locale.ROOT) + sep;
         contained = addonLower.startsWith(serverLower);
      } else {
         contained = realAddon.startsWith(realServer);
      }

      if (!contained) {
         return new ConvertAddonCommand.AddonRootCheck.Fail("path escapes the server root: " + realAddon);
      }

      boolean looksLikeAddon = Files.isDirectory(realAddon.resolve("cultures"))
         || Files.isDirectory(realAddon.resolve("goals"))
         || Files.isDirectory(realAddon.resolve("quests"))
         || Files.isDirectory(realAddon.resolve("languages"))
         || Files.exists(realAddon.resolve("itemlist.txt"));
      return !looksLikeAddon
         ? new ConvertAddonCommand.AddonRootCheck.Fail(
            "path does not look like a Millénaire legacy addon root (expected cultures/, goals/, quests/, languages/, or itemlist.txt)"
         )
         : new ConvertAddonCommand.AddonRootCheck.Ok(realAddon);
   }

   private static boolean isWindows() {
      return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
   }

   sealed interface AddonRootCheck permits ConvertAddonCommand.AddonRootCheck.Ok, ConvertAddonCommand.AddonRootCheck.Fail {
      record Fail(String reason) implements ConvertAddonCommand.AddonRootCheck {
      }

      record Ok(Path root) implements ConvertAddonCommand.AddonRootCheck {
      }
   }

   sealed interface Result
      permits ConvertAddonCommand.Result.EmptyPack,
      ConvertAddonCommand.Result.NotWritable,
      ConvertAddonCommand.Result.LockContention,
      ConvertAddonCommand.Result.AlreadyConverted,
      ConvertAddonCommand.Result.VersionDrift,
      ConvertAddonCommand.Result.Completed {
      record AlreadyConverted(Path addonRoot, Path outputRoot) implements ConvertAddonCommand.Result {
      }

      record Completed(LegacyConversionDriver.DriverResult driver) implements ConvertAddonCommand.Result {
      }

      record EmptyPack(Path addonRoot) implements ConvertAddonCommand.Result {
      }

      record LockContention(Path lockFile) implements ConvertAddonCommand.Result {
      }

      record NotWritable(Path addonRoot) implements ConvertAddonCommand.Result {
      }

      record VersionDrift(Path addonRoot, Path outputRoot, String recordedVersion, String runningVersion) implements ConvertAddonCommand.Result {
      }
   }
}
