package makinamania;

import java.util.List;

public class Post {
    private String id;
    private String reference;
    private String author;
    private String text;
    private List<String> quotes;
    private List<String> downloadLinks;
    private List<String> discogs;
    private List<String> images;
    private List<String> albumTitles;
    private String hoster;
    private boolean linkAlive = false;

    public Post(String id,
            String reference,
            String author,
            String text,
            List<String> quotes,
            List<String> downloadLinks,
            List<String> discogs,
            List<String> images,
            List<String> albumTitles,
            String hoster,
            boolean isAlive) {
        this.id = id;
        this.reference = reference;
        this.author = author;
        this.text = text;
        this.quotes = quotes;
        this.downloadLinks = downloadLinks;
        this.discogs = discogs;
        this.images = images;
        this.albumTitles = albumTitles;
        this.hoster = hoster;
        this.linkAlive = isAlive;

    }

    public Post() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getDownloadLinks() {
        return downloadLinks;
    }

    public void setDownloadLinks(List<String> downloadLinks) {
        this.downloadLinks = downloadLinks;
    }

    public List<String> getDiscogs() {
        return discogs;
    }

    public void setDiscogs(List<String> discogs) {
        this.discogs = discogs;
    }

    public List<String> getQuotes() {
        return quotes;
    }

    public void setQuotes(List<String> quotes) {
        this.quotes = quotes;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public List<String> getAlbumTitles() {
        return albumTitles;
    }

    public void setAlbumTitles(List<String> otherLinks) {
        this.albumTitles = otherLinks;
    }

    public String getHoster() {
        return hoster;
    }

    public void setHoster(String hoster) {
        this.hoster = hoster;
    }

    public boolean isLinkAlive() {
        return linkAlive;
    }

    public void setLinkAlive(boolean linkAlive) {
        this.linkAlive = linkAlive;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Post other = (Post) obj;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║ 🎵  ").append(cut(reference, 58)).append("\n");
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append("║ 🆔 ID:       ").append(id).append("\n");
        sb.append("║ 👤 Author:   ").append(author).append("\n");
        sb.append("║ 📦 Hoster:   ").append(hoster).append("\n");

        if (albumTitles != null && !albumTitles.isEmpty()) {
            sb.append("║ 💿 Albums:   ").append(albumTitles.size()).append("\n");
            for (String title : albumTitles) {
                sb.append("║   • ").append(title).append("\n");
            }
        }

        if (downloadLinks != null && !downloadLinks.isEmpty()) {
            sb.append("║ 📥 Downloads: ").append(downloadLinks.size()).append("\n");
        }

        if (discogs != null && !discogs.isEmpty()) {
            sb.append("║ 📀 Discogs:   ").append(discogs.size()).append("\n");
        }

        sb.append("╚══════════════════════════════════════════════════════════════╝");
        return sb.toString();
    }

    private String cut(String str, int max) {
        if (str == null)
            return "N/A";
        if (str.length() <= max)
            return String.format("%-" + max + "s", str);
        return str.substring(0, max - 3) + "...";
    }
}
