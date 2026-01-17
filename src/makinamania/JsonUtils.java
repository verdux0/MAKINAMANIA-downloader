package makinamania;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JsonUtils {

    private static final String DEFAULT_JSON_FILE = "resources/posts.json";
    private static final String DEFAULT_SCANNED_URLS_PATH = "resources/scanned.json";

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final TypeReference<List<Post>> POST_LIST_TYPE = new TypeReference<List<Post>>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {
    };

    private static final Object POSTS_LOCK = new Object();
    private static final Object URLS_LOCK = new Object();

    static {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static void toJson(List<Post> newPosts, String filePath) {
        synchronized (POSTS_LOCK) {
            Set<Post> allPosts = new LinkedHashSet<>();

            try {
                List<Post> existingPosts = loadPosts(filePath);
                allPosts.addAll(existingPosts);
            } catch (IOException e) {
                ConsoleLogger.info("Note: Could not load existing posts (creating new file): " + e.getMessage());
            }

            int beforeSize = allPosts.size();
            allPosts.addAll(newPosts);
            int afterSize = allPosts.size();

            ConsoleLogger.info("Added " + (afterSize - beforeSize) + " new unique posts.");

            saveAllPostsInternal(allPosts, filePath);
        }
    }

    public static void toJson(List<Post> newPosts) {
        toJson(newPosts, DEFAULT_JSON_FILE);
    }

    public static List<Post> loadPosts(String filePath) throws IOException {
        File jsonFile = new File(filePath);

        if (!jsonFile.exists()) {
            ConsoleLogger.warn("JSON file not found: " + filePath + ", returning empty list");
            return new ArrayList<>();
        }

        List<Post> posts = mapper.readValue(jsonFile, POST_LIST_TYPE);
        ConsoleLogger.success("Successfully loaded " + posts.size() + " posts from: " + filePath);
        return posts;
    }

    public static List<Post> loadPosts() throws IOException {
        return loadPosts(DEFAULT_JSON_FILE);
    }

    public static void saveAllPosts(List<Post> posts, String filePath) {
        synchronized (POSTS_LOCK) {
            saveAllPostsInternal(posts, filePath);
        }
    }

    public static void saveAllPosts(List<Post> posts) {
        saveAllPosts(posts, DEFAULT_JSON_FILE);
    }

    private static void saveAllPostsInternal(Object data, String filePath) {
        File jsonFile = new File(filePath);
        ensureDirectoryExists(jsonFile);

        try {
            mapper.writeValue(jsonFile, data);

            int size = data instanceof java.util.Collection ? ((java.util.Collection<?>) data).size() : 0;
            ConsoleLogger.success("Successfully saved " + size + " items to: " + filePath);

        } catch (IOException e) {
            ConsoleLogger.error("Error writing JSON file: " + e.getMessage());
        }
    }

    public static void saveScannedUrls(Set<String> scannedUrls, String filePath) {
        synchronized (URLS_LOCK) {
            saveAllPostsInternal(scannedUrls, filePath);
        }
    }

    public static void saveScannedUrls(Set<String> scannedUrls) {
        saveScannedUrls(scannedUrls, DEFAULT_SCANNED_URLS_PATH);
    }

    public static Set<String> loadScannedUrls(String filePath) {
        File jsonFile = new File(filePath);

        if (!jsonFile.exists()) {
            ConsoleLogger.info("ℹ Scanned URLs file not found, starting fresh");
            return ConcurrentHashMap.newKeySet();
        }

        try {
            List<String> urls = mapper.readValue(jsonFile, STRING_LIST_TYPE);

            Set<String> urlSet = ConcurrentHashMap.newKeySet(urls.size());
            urlSet.addAll(urls);

            ConsoleLogger.success("✓ Loaded " + urlSet.size() + " scanned URLs from: " + filePath);
            return urlSet;
        } catch (IOException e) {
            ConsoleLogger.error("✗ Error loading scanned URLs: " + e.getMessage());
            return ConcurrentHashMap.newKeySet();
        }
    }

    public static Set<String> loadScannedUrls() {
        return loadScannedUrls(DEFAULT_SCANNED_URLS_PATH);
    }

    public static List<String> filterNewUrls(List<String> allUrls, Set<String> processedUrls) {
        return allUrls.stream()
                .filter(url -> !processedUrls.contains(url))
                .collect(Collectors.toList());
    }

    public static void addUrlAndSave(Set<String> processedUrls, String url) {
        processedUrls.add(url);
    }

    private static void ensureDirectoryExists(File file) {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    public static String getDefaultJsonFilePath() {
        return DEFAULT_JSON_FILE;
    }

    public static boolean jsonFileExists() {
        return new File(DEFAULT_JSON_FILE).exists();
    }
}