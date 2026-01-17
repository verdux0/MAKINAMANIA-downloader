package makinamania;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ForoUtils {

    public static List<String> genUrls(String topicUrl, String input) throws IOException {
        List<String> urls = new ArrayList<>();
        int totalPages = getTotalPages(topicUrl);

        String baseUrl = extractBaseUrl(topicUrl);

        String[] parts = input.split(",");
        for (String part : parts) {
            part = part.trim();

            if (part.contains("-")) {
                String[] range = part.split("-");
                int start = Integer.parseInt(range[0].trim());

                int end;
                if (range[1].trim().equals("*")) {
                    end = totalPages;
                } else {
                    end = Integer.parseInt(range[1].trim());
                    if (end < start) {
                        System.err.println("Rango inválido: " + part + " (el número final debe ser >= inicial)");
                        continue; // Ignorar este rango
                    }
                }

                if (start < 1) start = 1;
                if (end > totalPages) end = totalPages;

                for (int i = start; i <= end; i++) {
                    urls.add(constructPageUrl(baseUrl, i));
                }
            }
            else if (part.equals("*")) {
                for (int i = 1; i <= totalPages; i++) {
                    urls.add(constructPageUrl(baseUrl, i));
                }
            } else {
                int num = Integer.parseInt(part.trim());
                if (num >= 1 && num <= totalPages) {
                    urls.add(constructPageUrl(baseUrl, num));
                }
            }
        }

        return urls;
    }

    private static String extractBaseUrl(String topicUrl) {
        return topicUrl.replaceAll("\\.\\d+\\.html$", ".");
    }

    private static String constructPageUrl(String baseUrl, int pageNumber) {
        int offset = (pageNumber - 1) * 15;
        return baseUrl + offset + ".html";
    }

    public static int getTotalPages(String topicUrl) throws IOException {
        String firstPageUrl = constructPageUrl(extractBaseUrl(topicUrl), 1);
        
        try {
            Document doc = Jsoup.connect(firstPageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            Elements pageLinks = doc.select("a.navPages");
            
            if (pageLinks.isEmpty()) {
                pageLinks = doc.select(".pagelinks a, .pagination a");
                
                if (pageLinks.isEmpty()) {
                    String bodyText = doc.body().text();
                    if (bodyText.matches(".*[Pp]ágina\\s+\\d+\\s+de\\s+(\\d+).*")) {
                        return Integer.parseInt(bodyText.replaceAll(".*[Pp]ágina\\s+\\d+\\s+de\\s+(\\d+).*", "$1"));
                    }
                    return 1;
                }
            }

            Element lastPage = pageLinks.last();
            String lastPageText = lastPage.text();
            
            if (lastPageText.matches("\\d+")) {
                return Integer.parseInt(lastPageText);
            }
            
            if (pageLinks.size() > 1) {
                Element secondLast = pageLinks.get(pageLinks.size() - 2);
                if (secondLast.text().matches("\\d+")) {
                    return Integer.parseInt(secondLast.text());
                }
            }

            return 1;

        } catch (IOException e) {
            System.err.println("Error obteniendo el número total de páginas: " + e.getMessage());
            return 1;
        }
    }
}