package makinamania;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Checker {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Pattern MEGA_REGEX = Pattern.compile("https://mega\\.nz/(file|folder)/([\\w-]+)#([\\w-]+)");

    public static boolean checkLink(String url, String hoster) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            switch (hoster.toLowerCase()) {
                case "swisstransfer", "drive", "dropbox", "wetransfer", "unknown":
                    return true;
                case "terabox":
                    return checkTeraBoxLink(url);
                case "mediafire":
                    return checkMediafireLink(url);
                case "rapidgator":
                    return checkRapidgatorLink(url);
                case "mega.nz":
                    return checkMegaLink(url);
                default:
                    return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean checkMegaLink(String url) {
        Matcher matcher = MEGA_REGEX.matcher(url);
        if (!matcher.matches()) {
            return false;
        }

        String type = matcher.group(1);
        String id = matcher.group(2);
        // String key = matcher.group(3); // Unused for now but captured

        String payload;
        if ("folder".equals(type)) {
            payload = "[{\"a\":\"f\",\"c\":1,\"r\":1,\"ca\":1}]";
        } else {
            // Simple JSON construction. Since ID is \w- (alphanumeric+dash), it's safe to
            // put in quotes without escaping.
            payload = String.format("[{\"a\":\"g\",\"p\":\"%s\"}]", id);
        }

        String randomId = String.valueOf((long) (Math.random() * 1_000_000_0000L));
        String requestUrl = "https://g.api.mega.co.nz/cs?id=" + randomId + "&n=" + id;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(requestUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body().trim();

            if (body.startsWith("[-")) {
                return false;
            } else if (body.startsWith("[{")) {
                return true;
            } else {
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean checkTeraBoxLink(String url) {
        return checkHeadRequest(url, HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_MOVED_TEMP);
    }

    public static boolean checkMediafireLink(String url) {
        return checkHeadRequest(url, HttpURLConnection.HTTP_OK);
    }

    public static boolean checkRapidgatorLink(String url) {
        return checkHeadRequest(url, HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_MOVED_TEMP);
    }

    public static boolean checkGenericLink(String url) {
        return checkHeadRequest(url, HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_MOVED_TEMP);
    }

    private static boolean checkHeadRequest(String urlString, int... validCodes) {
        try {
            URL url = URI.create(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            int responseCode = connection.getResponseCode();
            for (int code : validCodes) {
                if (responseCode == code) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}