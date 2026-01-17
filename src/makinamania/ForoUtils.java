package makinamania;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ForoUtils {

    /**
     * Generate forum page URLs from an input pattern.
     * Examples:
     *   "*" -> all pages
     *   "1-5" -> pages 1 to 5
     *   "1,2,4-10,300-*" -> mix of ranges, single pages, and up to the last page
     *
     * @param topicUrl full URL of the thread, e.g. https://www.makinamania.net/index.php/topic,189337.17475.html
     */
    public static List<String> genUrls(String topicUrl, String input) throws IOException {
        List<String> urls = new ArrayList<>();
        int totalPages = getTotalPages(topicUrl);

        // Construir la base URL correctamente
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
                    // Validación: el número final debe ser mayor que el inicial
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

    /**
     * Extrae la URL base sin el offset
     */
    private static String extractBaseUrl(String topicUrl) {
        // Eliminar la parte del offset (último número antes de .html)
        return topicUrl.replaceAll("\\.\\d+\\.html$", ".");
    }

    /**
     * Construye la URL para una página específica
     */
    private static String constructPageUrl(String baseUrl, int pageNumber) {
        // El offset es (página - 1) * 15
        int offset = (pageNumber - 1) * 15;
        return baseUrl + offset + ".html";
    }

    /**
     * Scrape the first page of the topic and determine how many total pages exist.
     */
    public static int getTotalPages(String topicUrl) throws IOException {
        // Usar la primera página (offset 0) para obtener la paginación
        String firstPageUrl = constructPageUrl(extractBaseUrl(topicUrl), 1);
        
        try {
            Document doc = Jsoup.connect(firstPageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(10000)
                    .get();

            // Buscar la paginación - seleccionar el último enlace de páginas
            Elements pageLinks = doc.select("a.navPages");
            
            if (pageLinks.isEmpty()) {
                // Si no hay paginación, buscar en otros elementos comunes de paginación
                pageLinks = doc.select(".pagelinks a, .pagination a");
                
                if (pageLinks.isEmpty()) {
                    // Verificar si hay algún texto que indique el número total de páginas
                    String bodyText = doc.body().text();
                    if (bodyText.matches(".*[Pp]ágina\\s+\\d+\\s+de\\s+(\\d+).*")) {
                        return Integer.parseInt(bodyText.replaceAll(".*[Pp]ágina\\s+\\d+\\s+de\\s+(\\d+).*", "$1"));
                    }
                    return 1; // Si no se encuentra paginación, asumir una página
                }
            }

            // Obtener el último enlace de paginación
            Element lastPage = pageLinks.last();
            String lastPageText = lastPage.text();
            
            // Intentar parsear el número
            if (lastPageText.matches("\\d+")) {
                return Integer.parseInt(lastPageText);
            }
            
            // Si el último enlace no es un número, buscar el penúltimo
            if (pageLinks.size() > 1) {
                Element secondLast = pageLinks.get(pageLinks.size() - 2);
                if (secondLast.text().matches("\\d+")) {
                    return Integer.parseInt(secondLast.text());
                }
            }

            return 1; // Fallback

        } catch (IOException e) {
            System.err.println("Error obteniendo el número total de páginas: " + e.getMessage());
            return 1; // Fallback a 1 página en caso de error
        }
    }
}