package cn.zbx1425.mtrsteamloco.data;

import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.render.integration.MtrModelRegistryUtil;
import cn.zbx1425.mtrsteamloco.render.scripting.train.ScriptedTrainRenderer;
import cn.zbx1425.mtrsteamloco.render.scripting.ScriptHolder;
import cn.zbx1425.sowcerext.util.ResourceUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import mtr.client.*;
import mtr.mappings.Text;
import mtr.mappings.Utilities;
import mtr.mappings.UtilitiesClient;
import mtr.sound.JonTrainSound;
import mtr.sound.TrainSoundBase;
import mtr.sound.bve.BveTrainSound;
import mtr.sound.bve.BveTrainSoundConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class ScriptedCustomTrains implements IResourcePackCreatorProperties, ICustomResources {

    public static void init(ResourceManager resourceManager) {
        ScriptedTrainRenderer.reInitiateScripts();
        readResource(resourceManager, mtr.MTR.MOD_ID + ":" + CUSTOM_RESOURCES_ID + ".json", jsonConfig -> {
            try {
                jsonConfig.get(CUSTOM_TRAINS_KEY).getAsJsonObject().entrySet().forEach(entry -> {
                    try {
                        final JsonObject jsonObject = entry.getValue().getAsJsonObject();

                        if (!jsonObject.has("script_files")) return;

                        final String name = getOrDefault(jsonObject, CUSTOM_TRAINS_NAME, entry.getKey(), JsonElement::getAsString);
                        final int color = getOrDefault(jsonObject, CUSTOM_TRAINS_COLOR, 0, jsonElement -> CustomResources.colorStringToInt(jsonElement.getAsString()));
                        final String trainId = CUSTOM_TRAIN_ID_PREFIX + entry.getKey();

                        final String description = getOrDefault(jsonObject, CUSTOM_TRAINS_DESCRIPTION, "", JsonElement::getAsString);
                        final String wikipediaArticle = getOrDefault(jsonObject, CUSTOM_TRAINS_WIKIPEDIA_ARTICLE, "", JsonElement::getAsString);
                        final float riderOffset = getOrDefault(jsonObject, CUSTOM_TRAINS_RIDER_OFFSET, 0f, JsonElement::getAsFloat);

                        final String bveSoundBaseId = getOrDefault(jsonObject, CUSTOM_TRAINS_BVE_SOUND_BASE_ID, "", JsonElement::getAsString);
                        final int speedSoundCount = getOrDefault(jsonObject, CUSTOM_TRAINS_SPEED_SOUND_COUNT, 0, JsonElement::getAsInt);
                        final String speedSoundBaseId = getOrDefault(jsonObject, CUSTOM_TRAINS_SPEED_SOUND_BASE_ID, "", JsonElement::getAsString);
                        final String doorSoundBaseId = getOrDefault(jsonObject, CUSTOM_TRAINS_DOOR_SOUND_BASE_ID, null, JsonElement::getAsString);
                        final float doorCloseSoundTime = getOrDefault(jsonObject, CUSTOM_TRAINS_DOOR_CLOSE_SOUND_TIME, 0.5f, JsonElement::getAsFloat);
                        final boolean accelSoundAtCoast = getOrDefault(jsonObject, CUSTOM_TRAINS_ACCEL_SOUND_AT_COAST, false, JsonElement::getAsBoolean);
                        final boolean constPlaybackSpeed = getOrDefault(jsonObject, CUSTOM_TRAINS_CONST_PLAYBACK_SPEED, false, JsonElement::getAsBoolean);

                        final boolean useBveSound;
                        if (StringUtils.isEmpty(bveSoundBaseId)) {
                            useBveSound = false;
                        } else {
                            if (jsonObject.has(CUSTOM_TRAINS_BVE_SOUND_BASE_ID)) {
                                useBveSound = true;
                            } else if (jsonObject.has(CUSTOM_TRAINS_SPEED_SOUND_BASE_ID)) {
                                useBveSound = false;
                            } else {
                                useBveSound = false;
                            }
                        }

                        final boolean hasGangwayConnection = getOrDefault(jsonObject, "has_gangway_connection", false, JsonElement::getAsBoolean);
                        final boolean isJacobsBogie = getOrDefault(jsonObject, "is_jacobs_bogie", false, JsonElement::getAsBoolean);
                        final float bogiePosition = getOrDefault(jsonObject, "bogie_position", 0f, JsonElement::getAsFloat);

                        if (jsonObject.has("script_files")) {
                            final String newBaseTrainType = jsonObject.get("base_type").getAsString().toLowerCase(Locale.ROOT);
                            TrainSoundBase trainSound = useBveSound
                                    ? new BveTrainSound(new BveTrainSoundConfig(resourceManager, bveSoundBaseId))
                                    : new JonTrainSound(speedSoundBaseId, new JonTrainSound.JonTrainSoundConfig(doorSoundBaseId, speedSoundCount, doorCloseSoundTime, accelSoundAtCoast, constPlaybackSpeed));

                            ScriptHolder scriptContext = new ScriptHolder();
                            Map<ResourceLocation, String> scripts = new Object2ObjectArrayMap<>();
                            if (jsonObject.has("script_texts")) {
                                JsonArray scriptTexts = jsonObject.get("script_texts").getAsJsonArray();
                                for (int i = 0; i < scriptTexts.size(); i++) {
                                    scripts.put(new ResourceLocation("mtrsteamloco", "script_texts/" + trainId + "/" + i),
                                            scriptTexts.get(i).getAsString());
                                }
                            }
                            JsonArray scriptFiles = jsonObject.get("script_files").getAsJsonArray();
                            for (int i = 0; i < scriptFiles.size(); i++) {
                                ResourceLocation scriptLocation = new ResourceLocation(scriptFiles.get(i).getAsString());
                                scripts.put(scriptLocation, ResourceUtil.readResource(resourceManager, scriptLocation));
                            }
                            scriptContext.load(scripts);

                            mtr.client.TrainClientRegistry.register(trainId, new TrainProperties(
                                    newBaseTrainType, Text.literal(name),
                                    description, wikipediaArticle, color,
                                    riderOffset, riderOffset, bogiePosition, isJacobsBogie, hasGangwayConnection,
                                    new ScriptedTrainRenderer(scriptContext),
                                    trainSound
                            ));
                        }
                    } catch (Exception ex) {
                        Main.LOGGER.error("Reading scripted custom train", ex);
                        MtrModelRegistryUtil.recordLoadingError("Failed loading Scripted Custom Train", ex);
                    }
                });
            } catch (Exception ignored) {
            }
        });

    }

    private static void readResource(ResourceManager manager, String path, Consumer<JsonObject> callback) {
        try {
            UtilitiesClient.getResources(manager, new ResourceLocation(path)).forEach(resource -> {
                try (final InputStream stream = Utilities.getInputStream(resource)) {
                    callback.accept(new JsonParser().parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (Exception e) { Main.LOGGER.error("On behalf of MTR: Parsing JSON " + path, e); }
                try {
                    Utilities.closeResource(resource);
                } catch (IOException e) { Main.LOGGER.error("On behalf of MTR: Closing resource " + path, e); }
            });
        } catch (Exception ignored) { }
    }

    private static <T> T getOrDefault(JsonObject jsonObject, String key, T defaultValue, Function<JsonElement, T> function) {
        if (jsonObject.has(key)) {
            return function.apply(jsonObject.get(key));
        } else {
            return defaultValue;
        }
    }
}
