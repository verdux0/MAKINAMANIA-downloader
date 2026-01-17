package makinamania;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Scraper {

    private static final Set<String> HOSTERS = Set.of(
            "swisstransfer", "mega.nz", "terabox", "mediafire", "rapidgator",
            "drive", "dropbox", "wetransfer");

    private static final Map<String, String> DISCOGS_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean stopRequested = false;

    public static void stop() {
        stopRequested = true;
    }

    public static void reset() {
        stopRequested = false;
    }

    public static List<Post> scrapePosts(String url) {
        if (stopRequested)
            return new ArrayList<>();

        try {
            Document doc = Jsoup.connect(url).timeout(10000).get();

            List<Post> posts = new ArrayList<>();
            for (Element element : doc.select("div.post")) {
                if (stopRequested)
                    break;

                Post post = parsePost(element);
                if (post != null && hasActiveLinks(post.getDownloadLinks(), post.getHoster())) {
                    posts.add(post);
                }
            }
            return posts;
        } catch (Exception e) {
            ConsoleLogger.error("Error scraping URL: " + url + " - " + e.getMessage());
            return new ArrayList<>();
        }
    }

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

    private static boolean hasActiveLinks(List<String> downloadLinks, String hoster) {
        if (stopRequested)
            return false;

        for (String link : downloadLinks) {
            if (stopRequested)
                return false;
            if (Checker2.checkLink(link, hoster)) {
                return true;
            }
        }
        return false;
    }

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

    private static String extractId(Element post) {
        Element parent = post.parent();
        if (parent == null)
            return "";
        Element link = parent.selectFirst("div[id^=subject_] a");
        String href = extractAttribute(link, "href");
        return normalizeId(href);
    }

    public static String normalizeId(String url) {
        if (url == null)
            return null;
        return url.replaceAll("\\?PHPSESSID=[^#]+", "");
    }

    private static String extractReference(Element post) {
        Element parent = post.parent();
        if (parent == null)
            return "";
        return extractText(parent.selectFirst("div[id^=subject_] a"));
    }

    private static String extractAuthor(Element post) {
        Element row = post.closest("tr");
        if (row == null)
            return "unknown";
        Element authorElement = row.selectFirst("td[valign=top][rowspan] div > b > span > a");
        return extractText(authorElement, "unknown");
    }

    private static String extractAttribute(Element element, String attribute) {
        return element != null ? element.attr(attribute) : "";
    }

    private static String extractText(Element element) {
        return element != null ? element.text() : "";
    }

    private static String extractText(Element element, String defaultValue) {
        return element != null ? element.text() : defaultValue;
    }

    private static List<String> extractLinks(Element post, Predicate<String> linkFilter) {
        Element content = post.clone();
        content.select(".bbc_standard_quote").remove();

        return content.select("a[href]").stream()
                .map(link -> link.attr("href"))
                .filter(linkFilter)
                .collect(Collectors.toList());
    }

    public static List<String> extractDownloadLinks(Element post) {
        return extractLinks(post, href -> {
            String lowerHref = href.toLowerCase();
            for (String hoster : HOSTERS) {
                if (lowerHref.contains(hoster))
                    return true;
            }
            return false;
        });
    }

    public static List<String> extractDiscogsLinks(Element post) {
        return extractLinks(post, href -> href.contains("discogs.com"));
    }

    public static List<String> extractQuotes(Element post) {
        return extractLinks(post, href -> href.contains("makinamania.net") && href.contains("msg"));
    }

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

    private static boolean isSmiley(String src) {
        return src.contains("makinamania.com/Smileys") && src.endsWith(".gif");
    }

    public static boolean isQuote(Post post) {
        String text = post.getText().toLowerCase().trim();
        return text.startsWith("cita") || text.startsWith("quote");
    }

    public static boolean addsNewContent(Post post) {
        List<String> quoteLinks = getQuoteDownloadLinks(post.getQuotes());
        return post.getDownloadLinks().stream()
                .anyMatch(link -> !quoteLinks.contains(link));
    }

    private static List<String> getQuoteDownloadLinks(List<String> quoteUrls) {
        if (quoteUrls.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Document doc = Jsoup.connect(quoteUrls.get(0)).timeout(10000).get();
            Element post = doc.selectFirst("div.post");
            return post != null ? extractDownloadLinks(post) : new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static List<String> extractAlbumTitles(List<String> discogsLinks) {
        return discogsLinks.stream()
                .map(Scraper::getDiscogsTitle)
                .filter(Objects::nonNull)
                .filter(title -> !title.isEmpty())
                .collect(Collectors.toList());
    }

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

    private static String extractTitleFromDiscogsUrl(String link) {
        if (link.matches(".*/release/\\d+-.*")) {
            String rawTitle = link.substring(link.lastIndexOf('/') + 1);
            rawTitle = rawTitle.replaceFirst("\\d+-", "");
            return rawTitle.replace("-", " ").trim();
        }
        return null;
    }

    private static String fetchDiscogsTitleFromWeb(String url) throws IOException {
        Document doc = Jsoup.connect(url)
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
}