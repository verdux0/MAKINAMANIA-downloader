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

    // Shared ObjectMapper instance (Optimización)
    private static final ObjectMapper mapper = new ObjectMapper();

    // Optimización 1: Reutilizar TypeReference para evitar instanciación constante
    private static final TypeReference<List<Post>> POST_LIST_TYPE = new TypeReference<List<Post>>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {
    };

    // Optimización 4: Locks separados para reducir contención
    private static final Object POSTS_LOCK = new Object();
    private static final Object URLS_LOCK = new Object();

    static {
        // Configure once
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    // --- Post Methods ---

    /**
     * Merge new posts with existing posts and save to file.
     * Synchronized on POSTS_LOCK to prevent file write collisions while allowing
     * URL operations.
     */
    public static void toJson(List<Post> newPosts, String filePath) {
        synchronized (POSTS_LOCK) {
            Set<Post> allPosts = new LinkedHashSet<>(); // Maintains insertion order, avoids duplicates

            // 1. Load existing posts (if any)
            try {
                List<Post> existingPosts = loadPosts(filePath);
                allPosts.addAll(existingPosts);
            } catch (IOException e) {
                // Logueamos aquí porque es una recuperación de error (crear nuevo archivo)
                ConsoleLogger.info("Note: Could not load existing posts (creating new file): " + e.getMessage());
            }

            // 2. Add new posts (deduplication happens here via Set)
            int beforeSize = allPosts.size();
            allPosts.addAll(newPosts);
            int afterSize = allPosts.size();

            ConsoleLogger.info("Added " + (afterSize - beforeSize) + " new unique posts.");

            // 3. Save merged list
            saveAllPostsInternal(allPosts, filePath);
        }
    }

    public static void toJson(List<Post> newPosts) {
        toJson(newPosts, DEFAULT_JSON_FILE);
    }

    /**
     * Load posts from file.
     * Optimización 3: Eliminado log redundante de error, el llamante decidirá.
     */
    public static List<Post> loadPosts(String filePath) throws IOException {
        File jsonFile = new File(filePath);

        if (!jsonFile.exists()) {
            ConsoleLogger.warn("JSON file not found: " + filePath + ", returning empty list");
            return new ArrayList<>();
        }

        // Optimización 1: Uso de constante POST_LIST_TYPE
        List<Post> posts = mapper.readValue(jsonFile, POST_LIST_TYPE);
        ConsoleLogger.success("Successfully loaded " + posts.size() + " posts from: " + filePath);
        return posts;
    }

    public static List<Post> loadPosts() throws IOException {
        return loadPosts(DEFAULT_JSON_FILE);
    }

    /**
     * Overwrites the file with the given list of posts.
     * Synchronized on POSTS_LOCK.
     */
    public static void saveAllPosts(List<Post> posts, String filePath) {
        synchronized (POSTS_LOCK) {
            saveAllPostsInternal(posts, filePath);
        }
    }

    public static void saveAllPosts(List<Post> posts) {
        saveAllPosts(posts, DEFAULT_JSON_FILE);
    }

    // Internal helper to avoid code duplication
    // No synch logic here, caller must provide it
    private static void saveAllPostsInternal(Object data, String filePath) {
        File jsonFile = new File(filePath);
        ensureDirectoryExists(jsonFile);

        try {
            mapper.writeValue(jsonFile, data);

            int size = 0;
            if (data instanceof java.util.Collection) {
                size = ((java.util.Collection<?>) data).size();
            }
            ConsoleLogger.success("Successfully saved " + size + " items to: " + filePath);

        } catch (IOException e) {
            ConsoleLogger.error("Error writing JSON file: " + e.getMessage());
        }
    }

    // --- URL Methods ---

    public static void saveScannedUrls(Set<String> scannedUrls, String filePath) {
        synchronized (URLS_LOCK) {
            // We enforce uniqueness via the input Set.
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
            return ConcurrentHashMap.newKeySet(); // Optimización 2: Mejor fábrica para Set concurrente
        }

        try {
            // Optimización 1: Uso de constante STRING_LIST_TYPE
            List<String> urls = mapper.readValue(jsonFile, STRING_LIST_TYPE);

            // Optimización 2: Inicialización directa
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

    /**
     * Filtra URLs nuevas que no han sido procesadas
     */
    public static List<String> filterNewUrls(List<String> allUrls, Set<String> processedUrls) {
        return allUrls.stream()
                .filter(url -> !processedUrls.contains(url))
                .collect(Collectors.toList());
    }

    public static void addUrlAndSave(Set<String> processedUrls, String url) {
        // Optimización 5: Limpieza de código muerto y mejora de lógica
        // Si processedUrls es concurrente (que lo es por loadScannedUrls), add es
        // seguro,
        // pero necesitamos atomisidad si fueramos a guardar inmediatamente.
        // Dado que guardar en cada add es costoso (IO), se recomienda hacerlo en batch.
        // Aquí solo la añadimos a memoria.
        processedUrls.add(url);
    }

    // --- Helpers ---

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