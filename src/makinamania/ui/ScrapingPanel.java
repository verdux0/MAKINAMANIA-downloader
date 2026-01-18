package makinamania.ui;

import makinamania.ConsoleLogger;
import makinamania.JsonUtils;
import makinamania.Post;
import makinamania.PostManager;
import makinamania.MakinamaniaScraper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScrapingPanel extends JPanel {
    private JTextField urlField;
    private JTextField boardPagesField;
    private JTextField pagesField;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel urlLabel;
    private JLabel boardPagesLabel;
    private JLabel pagesLabel;
    private JButton startButton;
    private JButton stopButton;
    private JTextPane consoleTextPane;

    private ScrapingWorker currentWorker;
    private final PostManager postManager;
    private final JTabbedPane mainTabbedPane;

    public ScrapingPanel(PostManager postManager, JTabbedPane mainTabbedPane) {
        this.postManager = postManager;
        this.mainTabbedPane = mainTabbedPane;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = createInputPanel();
        JPanel logoPanel = createLogoPanel();

        JPanel topPanel = new JPanel(new BorderLayout(20, 0));
        topPanel.add(inputPanel, BorderLayout.CENTER);
        topPanel.add(logoPanel, BorderLayout.EAST);

        JPanel northWrapper = new JPanel(new BorderLayout());

        JPanel progressPanel = createProgressPanel();
        progressPanel.setBorder(new EmptyBorder(20, 0, 0, 0));

        northWrapper.add(topPanel, BorderLayout.CENTER);
        northWrapper.add(progressPanel, BorderLayout.SOUTH);

        add(northWrapper, BorderLayout.NORTH);
        add(createConsolePanel(), BorderLayout.CENTER);
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Scraping Configuration"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        urlLabel = new JLabel("Topic:");
        panel.add(urlLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        urlField = new JTextField(30);
        urlField.setToolTipText("Input field for the user to enter the forum topic URL.");
        urlField.getDocument().addDocumentListener(new UrlValidationListener());
        panel.add(urlField, gbc);

        // Fila para páginas de board (se muestra solo si la URL es un board)
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        boardPagesLabel = new JLabel("Board pages:");
        panel.add(boardPagesLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        boardPagesField = new JTextField(30);
        boardPagesField.setToolTipText("Pages of the board to analyze (e.g. 1,2,5-10,*)");
        boardPagesField.getDocument().addDocumentListener(new BoardPagesValidationListener());
        panel.add(boardPagesField, gbc);

        // Fila para páginas de topic
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        pagesLabel = new JLabel("Topic pages:");
        panel.add(pagesLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        pagesField = new JTextField(30);
        pagesField.setToolTipText("EX:1,2,3,4,5-10,1000-* .... .");
        pagesField.getDocument().addDocumentListener(new PagesValidationListener());
        panel.add(pagesField, gbc);

        // Inicialmente el input de páginas de board está oculto
        boardPagesLabel.setVisible(false);
        boardPagesField.setVisible(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

        startButton = new JButton("Start Scraping");
        startButton.addActionListener(new StartScrapingListener());

        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> {
            MakinamaniaScraper.stop();
            if (currentWorker != null) {
                currentWorker.cancel(true);
            }
            statusLabel.setText("Stopping...");
            stopButton.setEnabled(false);
        });

        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(buttonPanel, gbc);

        return panel;
    }

    private JPanel createLogoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        ImageIcon icon = new ImageIcon(getClass().getResource("/LOGO.jpg"));
        Image image = icon.getImage().getScaledInstance(200, 200, Image.SCALE_SMOOTH);
        ImageIcon scaledIcon = new ImageIcon(image);

        JLabel logoLabel = new JLabel(scaledIcon, SwingConstants.CENTER);
        logoLabel.setPreferredSize(new Dimension(200, 200));
        logoLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        logoLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new URI("https://www.makinamania.net"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        panel.add(logoLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Progress"));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        statusLabel = new JLabel("Ready to start scraping...");
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));

        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(0, 120));
        return panel;
    }

    private JPanel createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Debug Console"));

        consoleTextPane = new JTextPane();
        consoleTextPane.setEditable(false);
        consoleTextPane.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(consoleTextPane);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        ConsoleLogger.addLogListener(message -> {
            SwingUtilities.invokeLater(() -> appendColoredMessage(message));
        });

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void appendColoredMessage(String message) {
        javax.swing.text.StyledDocument doc = consoleTextPane.getStyledDocument();
        javax.swing.text.SimpleAttributeSet style = new javax.swing.text.SimpleAttributeSet();

        Color color = UIManager.getColor("TextPane.foreground");
        if (color == null)
            color = Color.BLACK;

        if (message.contains("[SUCCESS]")) {
            color = new Color(0, 150, 0);
        } else if (message.contains("[ERROR]")) {
            color = Color.RED;
        } else if (message.contains("[WARN]")) {
            color = new Color(150, 100, 0);
        } else if (message.contains("[START]")) {
            color = Color.BLUE;
        }

        javax.swing.text.StyleConstants.setForeground(style, color);

        try {
            doc.insertString(doc.getLength(), message + "\n", style);
            consoleTextPane.setCaretPosition(doc.getLength());
        } catch (javax.swing.text.BadLocationException e) {
            e.printStackTrace();
        }
    }

    private class UrlValidationListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            validateUrl();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            validateUrl();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            validateUrl();
        }

        private void validateUrl() {
            String url = urlField.getText().trim();
            if (url.isEmpty()) {
                urlField.setBackground(Color.WHITE);
                urlLabel.setText("Topic:");
                boardPagesLabel.setVisible(false);
                boardPagesField.setVisible(false);
                return;
            }
            boolean isTopic = isTopicUrl(url);
            boolean isBoard = isBoardUrl(url);

            if (isTopic) {
                urlField.setBackground(new Color(200, 255, 200));
                urlLabel.setText("Topic:");
                boardPagesLabel.setVisible(false);
                boardPagesField.setVisible(false);
            } else if (isBoard) {
                urlField.setBackground(new Color(200, 255, 200));
                urlLabel.setText("Board:");
                boardPagesLabel.setVisible(true);
                boardPagesField.setVisible(true);
            } else {
                urlField.setBackground(new Color(255, 200, 200));
                urlLabel.setText("Board / Topic:");
                boardPagesLabel.setVisible(false);
                boardPagesField.setVisible(false);
            }
            urlField.getParent().revalidate();
            urlField.getParent().repaint();
        }
    }

    private class PagesValidationListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            validatePages();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            validatePages();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            validatePages();
        }

        private void validatePages() {
            String input = pagesField.getText().trim();
            if (input.isEmpty()) {
                pagesField.setBackground(Color.WHITE);
                return;
            }
            try {
                String[] parts = input.split(",");
                for (String part : parts) {
                    part = part.trim();
                    if (part.equals("*"))
                        continue;
                    if (part.contains("-")) {
                        String[] range = part.split("-");
                        if (range.length != 2)
                            throw new IllegalArgumentException();
                        Integer.parseInt(range[0].trim());
                        if (!range[1].trim().equals("*"))
                            Integer.parseInt(range[1].trim());
                    } else {
                        Integer.parseInt(part);
                    }
                }
                pagesField.setBackground(new Color(200, 255, 200));
            } catch (Exception e) {
                pagesField.setBackground(new Color(255, 200, 200));
            }
        }
    }

    private class BoardPagesValidationListener implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent e) {
            validateBoardPages();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            validateBoardPages();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            validateBoardPages();
        }

        private void validateBoardPages() {
            String input = boardPagesField.getText().trim();
            if (input.isEmpty()) {
                boardPagesField.setBackground(Color.WHITE);
                return;
            }
            try {
                String[] parts = input.split(",");
                for (String part : parts) {
                    part = part.trim();
                    if (part.equals("*"))
                        continue;
                    if (part.contains("-")) {
                        String[] range = part.split("-");
                        if (range.length != 2)
                            throw new IllegalArgumentException();
                        Integer.parseInt(range[0].trim());
                        if (!range[1].trim().equals("*"))
                            Integer.parseInt(range[1].trim());
                    } else {
                        Integer.parseInt(part);
                    }
                }
                boardPagesField.setBackground(new Color(200, 255, 200));
            } catch (Exception e) {
                boardPagesField.setBackground(new Color(255, 200, 200));
            }
        }
    }

    private class StartScrapingListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (!validateInputs())
                return;

            MakinamaniaScraper.reset();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);

            currentWorker = new ScrapingWorker();
            currentWorker.execute();
        }

        private boolean validateInputs() {
            String url = urlField.getText().trim();
            String topicPages = pagesField.getText().trim();
            String boardPages = boardPagesField.getText().trim();

            if (url.isEmpty() || topicPages.isEmpty()) {
                JOptionPane.showMessageDialog(ScrapingPanel.this,
                        "Please fill in URL and topic pages fields", "Input Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            if (urlField.getBackground().equals(new Color(255, 200, 200))) {
                JOptionPane.showMessageDialog(ScrapingPanel.this,
                        "Please enter a valid MakinaMania topic or board URL", "URL Error", JOptionPane.ERROR_MESSAGE);
                return false;
            }

            if (pagesField.getBackground().equals(new Color(255, 200, 200))) {
                JOptionPane.showMessageDialog(ScrapingPanel.this,
                        "Please enter valid page numbers (e.g., 1,2,3,4-7,8-*)", "Pages Error",
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }

            if (isBoardUrl(url)) {
                if (boardPages.isEmpty()) {
                    JOptionPane.showMessageDialog(ScrapingPanel.this,
                            "Please enter board pages when using a board URL", "Board Pages Error",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
                if (boardPagesField.getBackground().equals(new Color(255, 200, 200))) {
                    JOptionPane.showMessageDialog(ScrapingPanel.this,
                            "Please enter valid board page numbers (e.g., 1,2,3,4-7,8-*)", "Board Pages Error",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }
            return true;
        }
    }

    private class ScrapingWorker extends SwingWorker<Void, Integer> {
        private List<Post> posts;
        private int totalLinks;
        private int totalUrls;
        private final AtomicInteger completed = new AtomicInteger(0);
        private Set<String> scannedUrls;

        @Override
        protected Void doInBackground() throws Exception {
            String url = urlField.getText().trim();
            String topicPages = pagesField.getText().trim();
            String boardPages = boardPagesField.getText().trim();

            if (isBoardUrl(url)) {
                analyzeBoard(url, boardPages, topicPages);
            } else {
                analyzeTopic(url, topicPages);
            }
            return null;
        }

        private void analyzeBoard(String boardUrl, String boardPages, String topicPages) throws Exception {
            List<String> links = MakinamaniaScraper.getTopicPageUrls(boardUrl, boardPages, topicPages);
            processLinks(links);
        }

        private void analyzeTopic(String topicUrl, String topicPages) throws Exception {
            List<String> links = MakinamaniaScraper.genUrls(topicUrl, topicPages);
            processLinks(links);
        }

        private void processLinks(List<String> links) throws Exception {
            totalUrls = links.size();

            scannedUrls = JsonUtils.loadScannedUrls();
            List<String> linksToScrape = JsonUtils.filterNewUrls(links, scannedUrls);
            int skippedUrls = totalUrls - linksToScrape.size();
            totalLinks = linksToScrape.size();

            if (linksToScrape.isEmpty()) {
                ConsoleLogger.info("All URLs have already been scanned. No new URLs to process.");
                throw new IOException("All URLs have already been scanned. No new URLs to process.");
            }

            ConsoleLogger.start("Starting scraping of " + totalLinks + " new URLs (skipped " + skippedUrls + ")");
            posts = Collections.synchronizedList(new ArrayList<>());
            ExecutorService executor = Executors.newFixedThreadPool(3);

            try {
                List<Callable<Void>> tasks = new ArrayList<>();
                for (String link : linksToScrape) {
                    tasks.add(() -> {
                        if (isCancelled()) {
                            return null;
                        }
                        analyzePostPage(link, skippedUrls);
                        return null;
                    });
                }
                executor.invokeAll(tasks);
            } finally {
                executor.shutdown();
            }
        }

        private void analyzePostPage(String link, int skippedUrls) {
            try {
                ConsoleLogger.scraping(String.valueOf(completed.get() + 1), String.valueOf(totalUrls), link);
                List<Post> scrapedPosts = MakinamaniaScraper.scrapePosts(link);
                if (scrapedPosts != null && !scrapedPosts.isEmpty()) {
                    ConsoleLogger.success("Found " + scrapedPosts.size() + " posts in " + link);
                    posts.addAll(scrapedPosts);
                }
                JsonUtils.addUrlAndSave(scannedUrls, link);
                int currentCompleted = completed.incrementAndGet() + skippedUrls;
                int progress = (currentCompleted * 100) / totalUrls;
                publish(progress);
            } catch (Exception ex) {
                ConsoleLogger.error("Error scraping link: " + link + " -> " + ex.getMessage());
            }
        }

        @Override
        protected void process(List<Integer> progressValues) {
            if (!progressValues.isEmpty()) {
                int progress = progressValues.get(progressValues.size() - 1);
                progressBar.setValue(progress);
                int currentProcessed = (progress * totalUrls) / 100;
                statusLabel.setText(
                        "Progress: " + progress + "% - Processed " + currentProcessed + "/" + totalUrls + " URLs");
            }
        }

        @Override
        protected void done() {
            try {
                if (!isCancelled())
                    get();
                JsonUtils.saveScannedUrls(scannedUrls);

                if (posts.isEmpty()) {
                    if (!isCancelled()) {
                        JOptionPane.showMessageDialog(ScrapingPanel.this, "No posts found in the new URLs", "Warning",
                                JOptionPane.WARNING_MESSAGE);
                    } else {
                        statusLabel.setText("Scraping stopped. No new posts found so far.");
                    }
                } else {
                    Set<Post> uniquePosts = new HashSet<>(posts);
                    if (uniquePosts.size() < posts.size()) {
                        ConsoleLogger.warn("Filtered out " + (posts.size() - uniquePosts.size()) + " duplicate posts");
                    }
                    JsonUtils.toJson(new ArrayList<>(uniquePosts));

                    if (isCancelled()) {
                        statusLabel.setText("Scraping stopped. Saved " + uniquePosts.size() + " posts.");
                    } else {
                        progressBar.setValue(100);
                        statusLabel.setText("Scraping completed. " + uniquePosts.size() + " unique posts saved.");
                    }

                    postManager.updatePosts(new ArrayList<>(uniquePosts));
                    if (mainTabbedPane != null)
                        mainTabbedPane.setSelectedIndex(1);
                }

            } catch (Exception ex) {
                if (ex instanceof java.util.concurrent.CancellationException) {
                    statusLabel.setText("Scraping stopped by user.");
                    ConsoleLogger.stop("Scraping cancelled by user");
                } else {
                    JsonUtils.saveScannedUrls(scannedUrls);
                    JOptionPane.showMessageDialog(ScrapingPanel.this, "Error: " + ex.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Scraping failed - partial progress saved");
                }
                progressBar.setValue(0);
            } finally {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                currentWorker = null;
            }
        }
    }

    // Determina si una URL es de topic
    private boolean isTopicUrl(String url) {
        return url.startsWith("https://www.makinamania.net/index.php/topic") &&
                url.matches("^https://www\\.makinamania\\.net/index\\.php/topic,\\d+(\\.\\d+)?\\.html.*$");
    }

    // Determina si una URL es de board
    private boolean isBoardUrl(String url) {
        return url.startsWith("https://www.makinamania.net/index.php/board") &&
                url.matches("^https://www\\.makinamania\\.net/index\\.php/board,\\d+\\.\\d+.*$");
    }
}