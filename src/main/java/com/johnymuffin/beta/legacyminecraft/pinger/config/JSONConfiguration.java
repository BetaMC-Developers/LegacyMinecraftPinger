package com.johnymuffin.beta.legacyminecraft.pinger.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class JSONConfiguration {
    private Gson gson;
    protected File configFile;
    protected JsonObject jsonConfig;

    private boolean fileChanged = false;

    public JSONConfiguration(File file) {
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        this.configFile = file;
    }

    //Getters Start
    public String getConfigString(String key) {
        return jsonConfig.get(key).getAsString();
    }

    public Integer getConfigInteger(String key) {
        return jsonConfig.get(key).getAsInt();
    }

    public Long getConfigLong(String key) {
        return jsonConfig.get(key).getAsLong();
    }

    public Double getConfigDouble(String key) {
        return jsonConfig.get(key).getAsDouble();
    }

    public Boolean getConfigBoolean(String key) {
        return jsonConfig.get(key).getAsBoolean();
    }

    public void writeConfigurationFile() {
        if (fileChanged) {
            saveFile();
        }
    }

    //Getters End

    public boolean containsKey(String key) {
        return jsonConfig.has(key);
    }

    public void generateConfigOption(String key, Object value) {
        if (!jsonConfig.has(key)) {
            jsonConfig.add(key, gson.toJsonTree(value));
            fileChanged = true;
        }
    }

    public void writeConfigOption(String key, Object value) {
        jsonConfig.add(key, gson.toJsonTree(value));
        this.fileChanged = true;
    }

    public void load() {
        //Create directory
        if (!this.configFile.exists()) {
            this.configFile.getParentFile().mkdirs();
            jsonConfig = new JsonObject();
            saveFile();
        } else {
            try {
                jsonConfig = (JsonObject) JsonParser.parseString(new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8));
            } catch (JsonParseException e) {
                System.out.println("Failed to load config file.");
                throw new RuntimeException(e + ": " + e.getMessage());
            } catch (IOException e) {
                throw new RuntimeException(e + ": " + e.getMessage());
            }
        }
    }


    protected void saveFile() {
        try {
            Files.write(configFile.toPath(), gson.toJson(jsonConfig).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
