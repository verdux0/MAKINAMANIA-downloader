package makinamania;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ConsoleLogger {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void info(String message) {
        log("INFO", "ℹ️", message);
    }

    public static void success(String message) {
        log("SUCCESS", "✅", message);
    }

    public static void warn(String message) {
        log("WARN", "⚠️", message);
    }

    public static void error(String message) {
        log("ERROR", "❌", message);
    }

    public static void start(String message) {
        log("START", "🚀", message);
    }

    public static void stop(String message) {
        log("STOP", "🛑", message);
    }

    public static void debug(String message) {
        System.out.println(format("DEBUG", "🐛", message));
    }

    private static final java.util.List<java.util.function.Consumer<String>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addLogListener(java.util.function.Consumer<String> listener) {
        listeners.add(listener);
    }

    public static void logging(String message) {
        System.out.println(message);
        notifyListeners(message);
    }

    private static void log(String level, String emoji, String message) {
        String formatted = format(level, emoji, message);
        System.out.println(formatted);
        notifyListeners(formatted);
    }

    public static void scraping(String current, String total, String url) {
        String formatted = String.format("[%s] [SCRAPING] 🔍 Processing [%s/%s]: %s",
                LocalTime.now().format(TIME_FORMATTER), current, total, url);
        System.out.println(formatted);
        notifyListeners(formatted);
    }

    private static void notifyListeners(String message) {
        for (java.util.function.Consumer<String> listener : listeners) {
            listener.accept(message);
        }
    }

    private static String format(String level, String emoji, String message) {
        return String.format("[%s] [%s] %s %s",
                LocalTime.now().format(TIME_FORMATTER), level, emoji, message);
    }
}
