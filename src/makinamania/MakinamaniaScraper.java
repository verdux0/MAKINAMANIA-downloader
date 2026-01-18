package makinamania;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MakinamaniaScraper {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 15000;

    private static final Set<String> HOSTERS = Set.of(
            "swisstransfer", "mega.nz", "terabox", "mediafire", "rapidgator",
            "drive", "dropbox", "wetransfer");

    private static final Map<String, String> DISCOGS_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean stopRequested = false;

    // Representa la información básica de un topic.
    public static class TopicInfo {
        public final String url;
        public final String title;

        public TopicInfo(String url, String title) {
            this.url = url;
            this.title = title != null ? title.trim() : "";
        }
    }

    // Descarga una página genérica y devuelve el documento.
    private static Document fetchPage(String url, int timeoutMs) throws IOException {
        String cleanUrl = normalizeId(url);
        return Jsoup.connect(cleanUrl)
                .userAgent(USER_AGENT)
                .timeout(timeoutMs)
                .get();
    }

    // Obtiene el número total de páginas de un board.
    public static int getBoardTotalPages(String boardUrl) throws IOException {
        String cleanBoardUrl = normalizeId(boardUrl);
        Document doc = fetchBoardPage(cleanBoardUrl);
        return detectBoardTotalPages(doc);
    }

    // Descarga una página de board.
    private static Document fetchBoardPage(String boardUrl) throws IOException {
        return fetchPage(boardUrl, TIMEOUT_MS);
    }

    // Detecta el número máximo de página dentro de un board.
    private static int detectBoardTotalPages(Document doc) {
        Elements navPages = doc.select("a.navPages");
        int maxPage = 1;

        for (Element navPage : navPages) {
            try {
                int pageNumber = Integer.parseInt(navPage.text().trim());
                if (pageNumber > maxPage) {
                    maxPage = pageNumber;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return maxPage;
    }

    // Construye la URL de una página concreta de un board.
    private static String buildBoardPageUrl(String baseUrl, int pageNumber) {
        int offset = (pageNumber - 1) * 40;
        return baseUrl.replaceFirst("\\.\\d+", "." + offset);
    }

    // Extrae URLs de topics desde una página de board.
    private static List<String> extractTopicsFromBoard(Document doc) {
        List<String> topics = new ArrayList<>();
        Elements rows = doc.select("td.windowbg");

        for (Element row : rows) {
            Element topicLink = row.selectFirst("a");
            if (topicLink != null) {
                String href = topicLink.absUrl("href");
                topics.add(normalizeId(href));
            }
        }

        return topics;
    }

    // Extrae topics con títulos desde una página de board.
    public static List<TopicInfo> extractTopicsWithTitlesFromBoard(Document doc) {
        List<TopicInfo> topics = new ArrayList<>();
        Elements rows = doc.select("td.windowbg");

        for (Element row : rows) {
            Element topicLink = row.selectFirst("a");
            if (topicLink != null) {
                String href = topicLink.absUrl("href");
                String title = topicLink.text();
                topics.add(new TopicInfo(href, title));
            }
        }

        return topics;
    }

    // Resuelve qué páginas de un board se deben usar según el patrón.
    private static List<Integer> resolveBoardPages(String boardUrl, String boardPagesSpec) throws IOException {
        int totalPages = getBoardTotalPages(boardUrl);
        return parsePageSpec(boardPagesSpec, totalPages);
    }

    // Obtiene los topics raíz de un board para páginas concretas.
    public static List<String> getTopicsForBoardPages(String boardUrl, String boardPagesSpec) throws IOException {
        String cleanBoardUrl = normalizeId(boardUrl);
        List<Integer> pages = resolveBoardPages(cleanBoardUrl, boardPagesSpec);

        LinkedHashSet<String> topicSet = new LinkedHashSet<>();
        for (int page : pages) {
            String pageUrl = buildBoardPageUrl(cleanBoardUrl, page);
            Document pageDoc = fetchBoardPage(pageUrl);
            topicSet.addAll(extractTopicsFromBoard(pageDoc));
        }

        return new ArrayList<>(topicSet);
    }

    // Genera las URLs de páginas de topic para un board.
    public static List<String> getTopicPageUrls(String boardUrl, String boardPagesSpec, String topicPagesSpec)
            throws IOException {
        List<String> topicRoots = getTopicsForBoardPages(boardUrl, boardPagesSpec);
        List<String> topicPageUrls = new ArrayList<>();

        for (String topicUrl : topicRoots) {
            topicPageUrls.addAll(generateTopicPageUrls(topicUrl, topicPagesSpec));
        }

        return topicPageUrls;
    }

    // Obtiene los topics de un board organizados por página.
    public static List<List<TopicInfo>> getTopicsByPageForBoard(String boardUrl, String boardPagesSpec) throws IOException {
        String cleanBoardUrl = normalizeId(boardUrl);
        List<Integer> pages = resolveBoardPages(cleanBoardUrl, boardPagesSpec);

        List<List<TopicInfo>> topicsByPage = new ArrayList<>();
        for (int page : pages) {
            String pageUrl = buildBoardPageUrl(cleanBoardUrl, page);
            Document pageDoc = fetchBoardPage(pageUrl);
            List<TopicInfo> pageTopics = extractTopicsWithTitlesFromBoard(pageDoc);
            topicsByPage.add(pageTopics);
        }

        return topicsByPage;
    }

    // Parsea un patrón de páginas como "1,2,5-10,*".
    private static List<Integer> parsePageSpec(String input, int totalPages) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();

        if (input == null || input.trim().isEmpty()) {
            for (int i = 1; i <= totalPages; i++) {
                result.add(i);
            }
            return new ArrayList<>(result);
        }

        String[] parts = input.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (part.equals("*")) {
                for (int i = 1; i <= totalPages; i++) {
                    result.add(i);
                }
                continue;
            }

            if (part.contains("-")) {
                String[] range = part.split("-");
                if (range.length != 2) {
                    throw new IllegalArgumentException("Invalid range in page spec: " + part);
                }
                int start = Integer.parseInt(range[0].trim());
                String endStr = range[1].trim();
                int end = endStr.equals("*") ? totalPages : Integer.parseInt(endStr);
                if (start < 1) {
                    start = 1;
                }
                if (end > totalPages) {
                    end = totalPages;
                }
                for (int i = start; i <= end; i++) {
                    result.add(i);
                }
            } else {
                int page = Integer.parseInt(part);
                if (page >= 1 && page <= totalPages) {
                    result.add(page);
                }
            }
        }

        return new ArrayList<>(result);
    }

    // Extrae la URL base de un topic sin offset de página.
    private static String extractTopicBaseUrl(String topicUrl) {
        String clean = normalizeId(topicUrl);
        return clean.replaceAll("\\.\\d+\\.html$", ".");
    }

    // Construye la URL de una página concreta de un topic.
    private static String constructTopicPageUrl(String baseUrl, int pageNumber) {
        int offset = (pageNumber - 1) * 15;
        return baseUrl + offset + ".html";
    }

    // Descarga una página de topic.
    private static Document fetchTopicPage(String url) throws IOException {
        return fetchPage(url, 10000);
    }

    // Obtiene los enlaces de paginación de un topic.
    private static Elements getTopicPageLinks(Document doc) {
        Elements pageLinks = doc.select("a.navPages");
        if (pageLinks.isEmpty()) {
            pageLinks = doc.select(".pagelinks a, .pagination a");
        }
        return pageLinks;
    }

    // Obtiene el total de páginas a partir de los enlaces de paginación.
    private static int parseTotalPagesFromLinks(Elements pageLinks) {
        Element lastPage = pageLinks.last();
        if (lastPage != null && lastPage.text().matches("\\d+")) {
            return Integer.parseInt(lastPage.text());
        }

        if (pageLinks.size() > 1) {
            Element secondLast = pageLinks.get(pageLinks.size() - 2);
            if (secondLast.text().matches("\\d+")) {
                return Integer.parseInt(secondLast.text());
            }
        }

        return 1;
    }

    // Obtiene el total de páginas a partir del texto del cuerpo.
    private static int parseTotalPagesFromBody(Document doc) {
        String bodyText = doc.body().text();
        if (bodyText.matches(".*[Pp]ágina\\s+\\d+\\s+de\\s+(\\d+).*")) {
            return Integer.parseInt(bodyText.replaceAll(".*[Pp]ágina\\s+\\d+\\s+de\\s+(\\d+).*", "$1"));
        }
        return 1;
    }

    // Obtiene el número total de páginas de un topic.
    public static int getTopicTotalPages(String topicUrl) throws IOException {
        String firstPageUrl = constructTopicPageUrl(extractTopicBaseUrl(topicUrl), 1);

        try {
            Document doc = fetchTopicPage(firstPageUrl);
            Elements pageLinks = getTopicPageLinks(doc);

            if (!pageLinks.isEmpty()) {
                return parseTotalPagesFromLinks(pageLinks);
            }

            return parseTotalPagesFromBody(doc);
        } catch (IOException e) {
            System.err.println("Error obteniendo el número total de páginas: " + e.getMessage());
            return 1;
        }
    }

    // Alias de compatibilidad para obtener el total de páginas de un topic.
    public static int getTotalPages(String topicUrl) throws IOException {
        return getTopicTotalPages(topicUrl);
    }

    // Genera las URLs de páginas de un topic según un patrón.
    public static List<String> generateTopicPageUrls(String topicUrl, String input) throws IOException {
        int totalPages = getTopicTotalPages(topicUrl);
        List<Integer> pages = parsePageSpec(input, totalPages);

        String baseUrl = extractTopicBaseUrl(topicUrl);
        List<String> urls = new ArrayList<>();

        for (int page : pages) {
            urls.add(constructTopicPageUrl(baseUrl, page));
        }

        return urls;
    }

    // Alias de compatibilidad para generar URLs de topic.
    public static List<String> genUrls(String topicUrl, String input) throws IOException {
        return generateTopicPageUrls(topicUrl, input);
    }

    // Marca el scraping para que se detenga.
    public static void stop() {
        stopRequested = true;
    }

    // Reinicia el estado de parada del scraping.
    public static void reset() {
        stopRequested = false;
    }

    // Indica si se ha solicitado detener el scraping.
    public static boolean stopRequested() {
        return stopRequested;
    }

    // Scrapea los posts válidos de una página del foro.
    public static List<Post> scrapePosts(String url) {
        if (stopRequested) {
            return new ArrayList<>();
        }

        try {
            Document doc = fetchPage(url, 10000);
            return extractValidPostsFromDocument(doc);
        } catch (Exception e) {
            ConsoleLogger.error("Error scraping URL: " + normalizeId(url) + " - " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // Extrae los posts válidos de un documento HTML.
    private static List<Post> extractValidPostsFromDocument(Document doc) {
        List<Post> posts = new ArrayList<>();

        for (Element element : doc.select("div.post")) {
            if (stopRequested) {
                break;
            }

            Post post = parsePost(element);
            if (post != null && hasActiveLinks(post.getDownloadLinks(), post.getHoster())) {
                posts.add(post);
            }
        }

        return posts;
    }

    // Convierte un elemento HTML en un objeto Post.
    public static Post parsePost(Element post) {
        List<String> downloadLinks = extractDownloadLinks(post);

        if (downloadLinks.isEmpty()) {
            return null;
        }

        String hoster = extractHoster(downloadLinks);
        List<String> discogsLinks = extractDiscogsLinks(post);

        return new Post(
                extractId(post),
                extractReference(post),
                extractAuthor(post),
                post.text(),
                extractQuotes(post),
                downloadLinks,
                discogsLinks,
                extractImages(post),
                extractAlbumTitles(discogsLinks),
                hoster,
                false
        );
    }

    // Comprueba si una lista de enlaces contiene alguno activo.
    private static boolean hasActiveLinks(List<String> downloadLinks, String hoster) {
        if (stopRequested) {
            return false;
        }

        for (String link : downloadLinks) {
            if (stopRequested) {
                return false;
            }
            if (Checker.checkLink(link, hoster)) {
                return true;
            }
        }
        return false;
    }

    // Obtiene el hoster predominante de una lista de enlaces.
    public static String extractHoster(List<String> downloadLinks) {
        Map<String, Long> counts = new HashMap<>();

        for (String link : downloadLinks) {
            String lowerLink = link.toLowerCase();
            for (String hoster : HOSTERS) {
                if (lowerLink.contains(hoster)) {
                    counts.merge(hoster, 1L, Long::sum);
                }
            }
        }

        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
    }

    // Extrae el identificador de un post desde su HTML.
    private static String extractId(Element post) {
        Element parent = post.parent();
        if (parent == null) {
            return "";
        }
        Element link = parent.selectFirst("div[id^=subject_] a");
        String href = extractAttribute(link, "href");
        return normalizeId(href);
    }

    // Normaliza una URL eliminando el parámetro PHPSESSID.
    public static String normalizeId(String url) {
        if (url == null) {
            return null;
        }
        String result = url;
        // Elimina ?PHPSESSID=...& o ?PHPSESSID=...# o al final
        result = result.replaceAll("\\?PHPSESSID=[^&#]+(?=&|#|$)", "");
        // Elimina &PHPSESSID=...
        result = result.replaceAll("&PHPSESSID=[^&#]+", "");
        // Limpia ? sobrante al final
        if (result.endsWith("?")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    // Extrae el título de un post.
    private static String extractReference(Element post) {
        Element parent = post.parent();
        if (parent == null) {
            return "";
        }
        return extractText(parent.selectFirst("div[id^=subject_] a"));
    }

    // Extrae el autor de un post.
    private static String extractAuthor(Element post) {
        Element row = post.closest("tr");
        if (row == null) {
            return "unknown";
        }
        Element authorElement = row.selectFirst("td[valign=top][rowspan] div > b > span > a");
        return extractText(authorElement, "unknown");
    }

    // Extrae un atributo de un elemento HTML.
    private static String extractAttribute(Element element, String attribute) {
        return element != null ? element.attr(attribute) : "";
    }

    // Extrae el texto de un elemento HTML.
    private static String extractText(Element element) {
        return element != null ? element.text() : "";
    }

    // Extrae el texto de un elemento HTML con valor por defecto.
    private static String extractText(Element element, String defaultValue) {
        return element != null ? element.text() : defaultValue;
    }

    // Extrae enlaces de un post aplicando un filtro.
    private static List<String> extractLinks(Element post, Predicate<String> linkFilter) {
        Element content = post.clone();
        content.select(".bbc_standard_quote").remove();

        return content.select("a[href]").stream()
                .map(link -> link.attr("href"))
                .filter(linkFilter)
                .collect(Collectors.toList());
    }

    // Extrae los enlaces de descarga de un post.
    public static List<String> extractDownloadLinks(Element post) {
        return extractLinks(post, href -> {
            String lowerHref = href.toLowerCase();
            for (String hoster : HOSTERS) {
                if (lowerHref.contains(hoster)) {
                    return true;
                }
            }
            return false;
        });
    }

    // Extrae los enlaces de Discogs de un post.
    public static List<String> extractDiscogsLinks(Element post) {
        return extractLinks(post, href -> href.contains("discogs.com"));
    }

    // Extrae los enlaces de citas de un post.
    public static List<String> extractQuotes(Element post) {
        return extractLinks(post, href -> href.contains("makinamania.net") && href.contains("msg"));
    }

    // Extrae las imágenes de un post.
    public static List<String> extractImages(Element post) {
        List<String> images = extractLinks(post, href -> href.matches(".*(postimg|imgur|iili).*") ||
                href.matches(".*\\.(jpg|jpeg|png|gif)$"));

        images.addAll(
                post.select("img[src]").stream()
                        .map(img -> img.attr("src"))
                        .filter(src -> !isSmiley(src))
                        .collect(Collectors.toList()));

        return images;
    }

    // Indica si una URL de imagen corresponde a un smiley.
    private static boolean isSmiley(String src) {
        return src.contains("makinamania.com/Smileys") && src.endsWith(".gif");
    }

    // Indica si un post es solo una cita.
    public static boolean isQuote(Post post) {
        String text = post.getText().toLowerCase().trim();
        return text.startsWith("cita") || text.startsWith("quote");
    }

    // Indica si un post añade contenido nuevo.
    public static boolean addsNewContent(Post post) {
        List<String> quoteLinks = getQuoteDownloadLinks(post.getQuotes());
        return post.getDownloadLinks().stream()
                .anyMatch(link -> !quoteLinks.contains(link));
    }

    // Obtiene los enlaces de descarga de la primera cita referenciada.
    private static List<String> getQuoteDownloadLinks(List<String> quoteUrls) {
        if (quoteUrls.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Document doc = fetchPage(quoteUrls.get(0), 10000);
            Element post = doc.selectFirst("div.post");
            return post != null ? extractDownloadLinks(post) : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    // Extrae títulos de álbum desde enlaces de Discogs.
    public static List<String> extractAlbumTitles(List<String> discogsLinks) {
        return discogsLinks.stream()
                .map(MakinamaniaScraper::getDiscogsTitle)
                .filter(Objects::nonNull)
                .filter(title -> !title.isEmpty())
                .collect(Collectors.toList());
    }

    // Obtiene el título de un álbum desde un enlace de Discogs.
    private static String getDiscogsTitle(String link) {
        if (DISCOGS_CACHE.containsKey(link)) {
            return DISCOGS_CACHE.get(link);
        }

        try {
            String title = extractTitleFromDiscogsUrl(link);

            if (title == null) {
                title = fetchDiscogsTitleFromWeb(link);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (title != null && !title.isEmpty()) {
                DISCOGS_CACHE.put(link, title);
            }

            return title;
        } catch (Exception e) {
            return null;
        }
    }

    // Intenta obtener el título del álbum directamente desde la URL.
    private static String extractTitleFromDiscogsUrl(String link) {
        if (link.matches(".*/release/\\d+-.*")) {
            String rawTitle = link.substring(link.lastIndexOf('/') + 1);
            rawTitle = rawTitle.replaceFirst("\\d+-", "");
            return rawTitle.replace("-", " ").trim();
        }
        return null;
    }

    // Obtiene el título del álbum haciendo scraping en Discogs.
    private static String fetchDiscogsTitleFromWeb(String url) throws IOException {
        Document doc = Jsoup.connect(normalizeId(url))
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();

        Element h1 = doc.selectFirst("h1.MuiTypography-headLineXL.title_Brnd1");
        if (h1 != null) {
            String fullText = h1.text();
            String[] parts = fullText.split("–");
            return parts.length > 1 ? parts[1].trim() : fullText.trim();
        }
        return null;
    }
/* 
    // Main de prueba que analiza la página 2 del board y todas las páginas de sus tópicos
    public static void main(String[] args) {
        String defaultBoardUrl = "https://www.makinamania.net/index.php/board,52.40/sort,views/desc.html";
        String boardUrl = args.length > 0 ? args[0] : defaultBoardUrl;

        System.out.println("Board URL original: " + boardUrl);
        String cleanBoardUrl = normalizeId(boardUrl);
        System.out.println("Board URL limpia:   " + cleanBoardUrl);

        try {
            int totalBoardPages = getBoardTotalPages(cleanBoardUrl);
            System.out.println("Total páginas del board (detectadas): " + totalBoardPages);

            // Solo página 2 para el board
            List<String> topics = getTopicsForBoardPages(cleanBoardUrl, "2");
            System.out.println("Topics encontrados en la página 2: " + topics.size());

            if (topics.isEmpty()) {
                System.out.println("No se han encontrado topics.");
                return;
            }

            int totalTopics = topics.size();
            int totalTopicPages = 0;
            int totalPosts = 0;

            for (int i = 0; i < topics.size(); i++) {
                String topicUrl = topics.get(i);
                String cleanTopicUrl = normalizeId(topicUrl);

                System.out.println("\n=== Topic " + (i + 1) + " ===");
                System.out.println("URL topic limpia: " + cleanTopicUrl);

                int topicPages = getTopicTotalPages(cleanTopicUrl);
                totalTopicPages += topicPages;
                System.out.println("Páginas del topic: " + topicPages);

                // Todas las páginas del topic
                List<String> topicPageUrls = generateTopicPageUrls(cleanTopicUrl, "*");
                int topicPosts = 0;

                for (String topicPageUrl : topicPageUrls) {
                    List<Post> posts = scrapePosts(topicPageUrl);
                    topicPosts += posts.size();
                }

                totalPosts += topicPosts;
                System.out.println("Posts válidos en este topic: " + topicPosts);
            }

            System.out.println("\n=== Resumen página 2 del board ===");
            System.out.println("Total topics:       " + totalTopics);
            System.out.println("Total páginas topic: " + totalTopicPages);
            System.out.println("Total posts válidos: " + totalPosts);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
        */
}