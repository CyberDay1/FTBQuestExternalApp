package com.cyberday1.ftbqprobe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Minimal probe mod that dumps registry information when the dedicated server starts.
 */
@Mod(ProbeMod.MOD_ID)
public final class ProbeMod {
    public static final String MOD_ID = "ftbqprobe";

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final boolean includeNbt;
    private final Path outputDirectory;

    public ProbeMod() {
        String includeNbtProperty = System.getProperty("probe.includeNbt", "false");
        this.includeNbt = Boolean.parseBoolean(includeNbtProperty);
        this.outputDirectory = resolveOutputDirectory();

        NeoForge.EVENT_BUS.addListener(this::handleServerStarted);
    }

    private static Path resolveOutputDirectory() {
        String configured = System.getProperty("probe.out");
        Path base = (configured != null && !configured.isBlank()) ? Paths.get(configured) : Paths.get("probe_output");
        return base.toAbsolutePath().normalize();
    }

    private void handleServerStarted(final ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            LOGGER.error("Unable to create probe output directory {}", outputDirectory, e);
            return;
        }

        dumpRegistryData(server);
        dumpLanguages(server);
    }

    private void dumpRegistryData(final MinecraftServer server) {
        RegistryAccess access = server.registryAccess();
        JsonObject root = new JsonObject();

        root.add("registryNames", access.registries()
            .map(entry -> entry.key().location().toString())
            .sorted()
            .reduce(new JsonArray(), (array, value) -> {
                array.add(value);
                return array;
            }, (left, right) -> {
                right.forEach(left::add);
                return left;
            }));

        root.add("items", dumpEntryRegistry(access.registryOrThrow(Registries.ITEM), this::describeItem));
        root.add("blocks", dumpEntryRegistry(access.registryOrThrow(Registries.BLOCK), this::describeBlock));
        root.add("fluids", dumpEntryRegistry(access.registryOrThrow(Registries.FLUID), this::describeFluid));

        JsonObject tags = new JsonObject();
        tags.add("item", dumpTagNames(access.registryOrThrow(Registries.ITEM)));
        tags.add("block", dumpTagNames(access.registryOrThrow(Registries.BLOCK)));
        tags.add("fluid", dumpTagNames(access.registryOrThrow(Registries.FLUID)));
        root.add("tags", tags);

        writeJson(root, outputDirectory.resolve("registry_dump.json"));
    }

    private JsonArray dumpTagNames(final Registry<?> registry) {
        JsonArray array = new JsonArray();
        registry.getTagNames()
            .map(tag -> tag.location().toString())
            .sorted()
            .forEach(array::add);
        return array;
    }

    private <T> JsonArray dumpEntryRegistry(final Registry<T> registry, final Function<Map.Entry<ResourceKey<T>, T>, JsonObject> encoder) {
        return registry.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().location()))
            .map(encoder)
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }

    private JsonObject describeItem(final Map.Entry<ResourceKey<Item>, Item> entry) {
        JsonObject object = new JsonObject();
        ResourceLocation id = entry.getKey().location();
        Item item = entry.getValue();
        object.addProperty("id", id.toString());
        object.addProperty("descriptionId", item.getDescriptionId());
        object.addProperty("defaultName", Component.translatable(item.getDescriptionId()).getString());
        if (includeNbt) {
            ItemStack stack = item.getDefaultInstance();
            if (!stack.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                stack.save(tag);
                if (!tag.isEmpty()) {
                    object.addProperty("nbt", tag.toString());
                }
            }
        }
        return object;
    }

    private JsonObject describeBlock(final Map.Entry<ResourceKey<Block>, Block> entry) {
        JsonObject object = new JsonObject();
        ResourceLocation id = entry.getKey().location();
        Block block = entry.getValue();
        object.addProperty("id", id.toString());
        object.addProperty("descriptionId", block.getDescriptionId());
        object.addProperty("defaultName", Component.translatable(block.getDescriptionId()).getString());
        return object;
    }

    private JsonObject describeFluid(final Map.Entry<ResourceKey<Fluid>, Fluid> entry) {
        JsonObject object = new JsonObject();
        ResourceLocation id = entry.getKey().location();
        Fluid fluid = entry.getValue();
        object.addProperty("id", id.toString());
        object.addProperty("descriptionId", fluid.getFluidType().getDescriptionId());
        object.addProperty("defaultName", Component.translatable(fluid.getFluidType().getDescriptionId()).getString());
        return object;
    }

    private void dumpLanguages(final MinecraftServer server) {
        ResourceManager resourceManager = server.getServerResources().resourceManager();
        Map<String, Map<String, String>> languages = new TreeMap<>();

        final Map<ResourceLocation, Resource> resources;
        try {
            resources = resourceManager.listResources("lang", location -> location.getPath().endsWith(".json"));
        } catch (IOException e) {
            LOGGER.error("Failed to enumerate language resources", e);
            return;
        }
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation resourceLocation = entry.getKey();
            String path = resourceLocation.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            String languageCode = fileName.substring(0, fileName.length() - ".json".length()).toLowerCase(Locale.ROOT);
            Map<String, String> translations = languages.computeIfAbsent(languageCode, ignored -> new TreeMap<>());
            try (Reader reader = new BufferedReader(entry.getValue().openAsReader())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    for (Map.Entry<String, JsonElement> element : json.entrySet()) {
                        JsonElement value = element.getValue();
                        if (value.isJsonPrimitive()) {
                            translations.putIfAbsent(element.getKey(), value.getAsString());
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to read language file {}", resourceLocation, e);
            }
        }

        JsonObject output = new JsonObject();
        for (Map.Entry<String, Map<String, String>> entry : languages.entrySet()) {
            JsonObject langJson = new JsonObject();
            entry.getValue().forEach(langJson::addProperty);
            output.add(entry.getKey(), langJson);
        }

        writeJson(output, outputDirectory.resolve("lang_index.json"));
    }

    private void writeJson(final JsonObject json, final Path target) {
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write JSON output {}", target, e);
        }
    }
}
