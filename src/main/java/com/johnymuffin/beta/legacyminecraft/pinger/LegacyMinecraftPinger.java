package com.johnymuffin.beta.legacyminecraft.pinger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.johnymuffin.beta.legacyminecraft.pinger.config.JSONConfiguration;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class LegacyMinecraftPinger extends Plugin {
    //Basic Plugin Info
    private static LegacyMinecraftPinger plugin;
    private String pluginName;
    private PluginDescription pdf;
    private ScheduledTask task;
    private String serverIcon;
    private Gson gson;

    private JSONConfiguration LMPConfig;

    private boolean firstPing = true;

    @Override
    public void onEnable() {
        plugin = this;
        pdf = this.getDescription();
        pluginName = pdf.getName();
        gson = new GsonBuilder().setLenient().disableHtmlEscaping().create();
        logger(Level.INFO, "Is Loading, Version: " + pdf.getVersion());

        //Generate Config
        File configFile = new File(this.getDataFolder(), "config.json");
        boolean newConfig = !configFile.exists();
        LMPConfig = new JSONConfiguration(configFile);

        LMPConfig.load();

        LMPConfig.generateConfigOption("config-version", 1);
        LMPConfig.generateConfigOption("url", "https://servers.api.legacyminecraft.com/api/v1/serverPing");
        LMPConfig.generateConfigOption("serverName", "My Test Server");
        LMPConfig.generateConfigOption("description", "My server is pretty nice, you should check it out!");
        LMPConfig.generateConfigOption("version", "B1.7.3");
        LMPConfig.generateConfigOption("serverIP", "mc.retromc.org");
        LMPConfig.generateConfigOption("serverPort", 25565);
        LMPConfig.generateConfigOption("onlineMode", false);
        LMPConfig.generateConfigOption("serverOwner", "ThatGuy");
        LMPConfig.generateConfigOption("pingTime", 45);
        LMPConfig.generateConfigOption("maxPlayers", BungeeCord.getInstance().config.getPlayerLimit());
        LMPConfig.generateConfigOption("key.info", "A key is required if you want to list your server with a image and have it be authenticated. Please contact Johny Muffin#9406 on Discord for a key, or email legacykey@johnymuffin.com to get one.");
        LMPConfig.generateConfigOption("key.value", "");
        LMPConfig.generateConfigOption("debug", false);

        LMPConfig.generateConfigOption("settings.show-cords.value", false);
        LMPConfig.generateConfigOption("settings.show-cords.info", "Makes the coordinates of players accessible via the API.");

        LMPConfig.generateConfigOption("settings.force-server-uuid.enabled", false);
        LMPConfig.generateConfigOption("settings.force-server-uuid.value", "");
        LMPConfig.generateConfigOption("settings.force-server-uuid.info", "Allows a server owner to force the UUID the server. This is recommend once you receive a key meaning your UUID won't change if any of your details do. YOUR SERVER MUST HAVE A VALID KEY FOR THE APPROPRIATE UUID TO USE THIS SETTING.");

        LMPConfig.generateConfigOption("flags.BetaEvolutions.enabled", false);
        LMPConfig.generateConfigOption("flags.BetaEvolutions.info", "Enabled this if your server runs Beta Evolutions");
        LMPConfig.generateConfigOption("flags.MineOnline.enabled", true);
        LMPConfig.generateConfigOption("flags.MineOnline.info", "Enable this flag if you want your server to be listed on the MineOnline launcher.");

        LMPConfig.writeConfigurationFile();

        //Verify UUID string
        verifyUUIDString();

        if (newConfig) {
            logger(Level.WARNING, "Stopping the plugin as the config needs to be set correctly.");
            onDisable();
            return;
        }

        //Load Image
        File serverIconFile = new File(plugin.getDataFolder(), "server-icon.png");
        if (serverIconFile.exists()) {
            logger(Level.INFO, "Loading Server Icon into cache.");
            serverIcon = loadIcon(serverIconFile);
            if (serverIcon != null) {
                logger(Level.INFO, "Server Icon has been loaded into cache.");
            }
        }


        task = getProxy().getScheduler().schedule(plugin, () -> {
            final String jsonData = gson.toJson(generateJsonData());
            final String apiURL = LMPConfig.getConfigString("url");
            getProxy().getScheduler().runAsync(plugin, () -> {
                //Post code directly copied from: https://github.com/codieradical/MineOnlineBroadcast-Bukkit/blob/master/src/MineOnlineBroadcast.java
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(apiURL);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestMethod("POST");
                    connection.setDoInput(true);
                    connection.setDoOutput(true);

                    connection.getOutputStream().write(jsonData.getBytes(StandardCharsets.UTF_8));
                    connection.getOutputStream().flush();
                    connection.getOutputStream().close();

                    InputStream is = connection.getInputStream();
                    BufferedReader rd = new BufferedReader(new InputStreamReader(is));

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = rd.readLine()) != null) {
                        response.append(line);
                        response.append('\r');
                    }

                    try {
                        JsonObject jsonResponse = (JsonObject) JsonParser.parseString(response.toString());
                        if (jsonResponse.has("notice")) {
                            logger(Level.INFO, "Message from API: " + jsonResponse.get("notice").getAsString());
                        }
                        //Logic to run on first successful ping.
                        if (firstPing) {
                            //Automatically enforce key if server is authenticated.
                            if (jsonResponse.get("authenticated").getAsBoolean() && !LMPConfig.getConfigBoolean("settings.force-server-uuid.enabled") && LMPConfig.getConfigString("settings.force-server-uuid.value").isEmpty()) {
                                logger(Level.INFO, "-------------------[" + plugin.getDescription().getName() + "]-------------------");
                                logger(Level.INFO, "Enabling key enforcement as server is authenticated and this can prevent issues if your details every change.\n" +
                                        "If you ever want to disable this, remove your authentication key, and set uuid override to disabled in the config file then restart.");
                                UUID serverUUID = UUID.fromString(jsonResponse.get("uuid").getAsString());
                                LMPConfig.writeConfigOption("settings.force-server-uuid.enabled", true);
                                LMPConfig.writeConfigOption("settings.force-server-uuid.value", serverUUID.toString());
                                LMPConfig.writeConfigurationFile();
                                //Verify UUID incase bad UUID was returned
                                verifyUUIDString();
                                logger(Level.INFO, "-----------------------------------------------");
                            }
                            firstPing = false;
                        }

                        //Allow the API to direct the plugin to set a key. This will be used in the future for automatic verification.
                        if (jsonResponse.has("newKey")) {
                            if (jsonResponse.get("authenticated").getAsBoolean()) {
                                logger(Level.INFO, "Your server has been remotely authenticated by the API.");
                            }
                            logger(Level.INFO, "The API has provided the authentication key" + jsonResponse.get("newKey").getAsString() + ". Automatically setting this key in the config.");
                            LMPConfig.writeConfigOption("key.value", jsonResponse.get("newKey").getAsString());
                            LMPConfig.writeConfigurationFile();
                        }


                    } catch (Exception e) {
                        logger(Level.INFO, "Malformed JSON response after ping returned normal status code: " + e + ": " + e.getMessage());
                        return;
                    }

                    rd.close();
                } catch (Exception e) {
                    logger(Level.WARNING, "An error occurred when attempting to ping: " + e + ": " + e.getMessage());
                    if (this.LMPConfig.getConfigBoolean("debug")) {
                        logger(Level.WARNING, "Ping Object: " + jsonData);
                    }
                } finally {
                    if (connection != null)
                        connection.disconnect();
                }
            });

        }, 20, LMPConfig.getConfigInteger("pingTime"), TimeUnit.SECONDS);


    }


    public void verifyUUIDString() {
        if (LMPConfig.getConfigBoolean("settings.force-server-uuid.enabled")) {
            try {
                String uuidString = LMPConfig.getConfigString("settings.force-server-uuid.value");
                UUID uuid = UUID.fromString(uuidString);
            } catch (Exception e) {
                logger(Level.WARNING, "A invalid UUID has been specified. The setting is being disabled.");
                LMPConfig.writeConfigOption("settings.force-server-uuid.enabled", false);
                LMPConfig.writeConfigOption("settings.force-server-uuid.value", "");
                LMPConfig.writeConfigurationFile();
            }
        }
    }

    @Override
    public void onDisable() {
        logger(Level.INFO, "Disabling.");
        if (task != null) {
            getProxy().getScheduler().cancel(task);
        }
    }

    public void logger(Level level, String message) {
        getProxy().getLogger().log(level, "[" + pluginName + "] " + message);
    }

    public JsonObject generateJsonData() {
        JsonObject tmp = new JsonObject();
        tmp.addProperty("name", LMPConfig.getConfigString("serverName"));
        tmp.addProperty("description", LMPConfig.getConfigString("description"));
        tmp.addProperty("version", LMPConfig.getConfigString("version"));
        tmp.addProperty("ip", LMPConfig.getConfigString("serverIP"));
        tmp.addProperty("port", LMPConfig.getConfigInteger("serverPort"));
        tmp.addProperty("onlineMode", LMPConfig.getConfigBoolean("onlineMode"));
        tmp.addProperty("maxPlayers", LMPConfig.getConfigString("maxPlayers"));
        tmp.addProperty("key", LMPConfig.getConfigString("key.value"));
        tmp.addProperty("show-cords", LMPConfig.getConfigBoolean("settings.show-cords.value"));
        if (LMPConfig.getConfigBoolean("settings.force-server-uuid.enabled")) {
            tmp.addProperty("uuid", UUID.fromString(LMPConfig.getConfigString("settings.force-server-uuid.value")).toString()); //Error out at the server level if UUID is invalid.
        }

        JsonArray playerArray = new JsonArray();
        for (ProxiedPlayer p : getProxy().getPlayers()) {
            JsonObject playerData = new JsonObject();
            playerData.addProperty("username", p.getName());
            playerData.addProperty("uuid", generateOfflineUUID(p.getName()).toString());
            playerData.addProperty("x", 0.0);
            playerData.addProperty("y", 0.0);
            playerData.addProperty("z", 0.0);
            playerData.addProperty("world", p.getServer().getInfo().getName());
            //TODO: Seconds Online
            playerData.addProperty("secondsOnline", 0);
            playerArray.add(playerData);
        }
        tmp.add("players", playerArray);
        tmp.addProperty("playersOnline", playerArray.size());
        //Flags - Start
        JsonArray flags = new JsonArray();
        JsonObject betaEVOFlag = new JsonObject();
        betaEVOFlag.addProperty("enabled", LMPConfig.getConfigBoolean("flags.BetaEvolutions.enabled"));
        betaEVOFlag.addProperty("name", "BetaEvolutions");
        flags.add(betaEVOFlag);
        JsonObject mineOnlineFlag = new JsonObject();
        mineOnlineFlag.addProperty("name", "MineOnline");
        mineOnlineFlag.addProperty("enabled", LMPConfig.getConfigBoolean("flags.MineOnline.enabled"));
        flags.add(mineOnlineFlag);
        //Flags - End
        tmp.add("flags", flags);
        tmp.addProperty("protocol", 1);
        tmp.addProperty("pluginName", pluginName);
        tmp.addProperty("pluginVersion", pdf.getVersion());
        if (serverIcon != null) {
            tmp.addProperty("serverIcon", serverIcon);
        }
        return tmp;
    }

    public static UUID generateOfflineUUID(String username) {
        return UUID.nameUUIDFromBytes(username.getBytes());
    }

    private String loadIcon(File file) {
        try {
            BufferedImage bufferedImage = ImageIO.read(file);
            if (!(bufferedImage.getHeight() == 64 && bufferedImage.getWidth() == 64)) {
                logger(Level.INFO, "The server-icon image has invalid dimensions, " + bufferedImage.getHeight() + "x" + bufferedImage.getWidth() + ". Try 64x64");
                return null;
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", output);
            String base64String = Base64.getEncoder().encodeToString(output.toByteArray());
            base64String = base64String.replace("\n", "");
            return base64String;
        } catch (Exception e) {
            logger(Level.WARNING, "An error occurred reading the server-icon");
            e.printStackTrace();
        }
        return null;

    }


}
