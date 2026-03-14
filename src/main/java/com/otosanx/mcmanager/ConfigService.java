package com.otosanx.mcmanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigService {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path CONFIG_DIR = Paths.get(System.getProperty("user.home"), ".mc-server-manager");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    public static AppConfig load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(CONFIG_FILE)) {
                return MAPPER.readValue(CONFIG_FILE.toFile(), AppConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new AppConfig();
    }

    public static void save(AppConfig config) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        MAPPER.writeValue(CONFIG_FILE.toFile(), config);
    }

    public static Path getConfigFile() {
        return CONFIG_FILE;
    }
}
