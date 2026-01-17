package makinamania.ui;

import makinamania.JsonUtils;
import makinamania.Post;
import makinamania.PostManager;
import makinamania.SearchDocumentListener;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class DataPanel extends JPanel {
    private JList<Post> postsList;
    private DefaultListModel<Post> listModel;
    private JEditorPane postDetailsArea;
    private JLabel postCountLabel;
    private JTextField searchField;
    private PostManager postManager;

    private static final List<String> HOSTERS = Arrays.asList("swisstransfer", "mega.nz", "terabox", "mediafire",
            "rapidgator", "drive", "dropbox", "wetransfer");

    public DataPanel(PostManager pm) {
        initializeUI();
        if (pm != null) {
            setPostManager(pm);
        }
    }

    // Getters para que MainApp pueda configurar PostManager
    public DefaultListModel<Post> getListModel() {
        return listModel;
    }

    public JLabel getPostCountLabel() {
        return postCountLabel;
    }

    public JTextField getSearchField() {
        return searchField;
    }

    public void setPostManager(PostManager pm) {
        this.postManager = pm;
        // Ahora podemos añadir los listeners que dependen de postManager
        if (searchField != null) {
            searchField.getDocument().addDocumentListener(new SearchDocumentListener(postManager));
        }
    }

    private void initializeUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top panel with count and buttons
        add(createDataTopPanel(), BorderLayout.NORTH);

        // Split pane for list and details
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(createPostsListPanel());
        splitPane.setRightComponent(createPostDetailsPanel());
        splitPane.setDividerLocation(400);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createDataTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        postCountLabel = new JLabel("Posts: 0");

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(postCountLabel);

        JPanel controlsPanel = new JPanel(new BorderLayout());
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(null);

        JButton loadButton = new JButton("Reload Posts");
        loadButton.setToolTipText("Reload all posts from JSON file");
        loadButton.addActionListener(e -> loadPosts());
        toolBar.add(loadButton);

        JButton deleteButton = new JButton("Delete Selected");
        deleteButton.addActionListener(e -> deleteSelectedPosts());
        toolBar.add(deleteButton);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel searchLabel = new JLabel("Search:");
        searchPanel.add(searchLabel);
        searchField = new JTextField(20);
        searchPanel.add(searchField);

        JComboBox<String> hosterComboBox = new JComboBox<>();
        hosterComboBox.addItem("Todos");
        for (String hoster : HOSTERS)
            hosterComboBox.addItem(hoster);
        hosterComboBox.setMaximumSize(new Dimension(150, 25));
        hosterComboBox.addActionListener(e -> {
            if (postManager == null)
                return;
            String selected = (String) hosterComboBox.getSelectedItem();
            if (selected == null)
                return;
            postManager.filterPostsByHoster("Todos".equals(selected) ? "" : selected);
        });

        rightPanel.add(searchPanel);
        rightPanel.add(hosterComboBox);

        controlsPanel.add(toolBar, BorderLayout.WEST);
        controlsPanel.add(rightPanel, BorderLayout.EAST);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(controlsPanel, BorderLayout.CENTER);

        return panel;
    }

    private JScrollPane createPostsListPanel() {
        listModel = new DefaultListModel<>();
        postsList = new JList<>(listModel);
        postsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        postsList.setCellRenderer(new PostListRenderer());
        postsList.addListSelectionListener(e -> showPostDetails());

        JScrollPane scrollPane = new JScrollPane(postsList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Posts List"));
        return scrollPane;
    }

    private JScrollPane createPostDetailsPanel() {
        postDetailsArea = new JEditorPane();
        postDetailsArea.setContentType("text/html");
        postDetailsArea.setEditable(false);
        postDetailsArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        postDetailsArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(postDetailsArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Post Details"));
        return scrollPane;
    }

    private void loadPosts() {
        if (postManager == null)
            return;
        try {
            List<Post> posts = JsonUtils.loadPosts();
            postManager.updatePosts(posts);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error loading posts: " + ex.getMessage(), "Load Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedPosts() {
        if (postManager == null)
            return;
        int[] indices = postsList.getSelectedIndices();
        if (indices.length == 0)
            return;

        int result = JOptionPane.showConfirmDialog(this,
                "Delete " + indices.length + " selected posts?", "Confirm Delete", JOptionPane.YES_NO_OPTION);

        if (result == JOptionPane.YES_OPTION) {
            try {
                postManager.deleteSelectedPosts(postsList);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error deleting posts: " + ex.getMessage(), "Delete Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showPostDetails() {
        Post selected = postsList.getSelectedValue();
        if (selected == null) {
            postDetailsArea.setText("");
            return;
        }

        StringBuilder details = new StringBuilder();
        details.append("<html><body style='font-family:sans-serif; padding:10px;'>");
        if (selected.getAlbumTitles() != null && !selected.getAlbumTitles().isEmpty()) {
            details.append("<b>Album Titles:</b><br>");
            for (String title : selected.getAlbumTitles()) {
                details.append("  - ").append(title).append("<br>");
            }
            details.append("<hr>");
        }
        details.append("<table border='0' cellspacing='5' cellpadding='2' style='font-family:monospace;'>");
        details.append("<tr><td><b>Reference:</b></td><td>").append(selected.getReference()).append("</td></tr>");
        details.append("<tr><td><b>ID:</b></td><td>").append(selected.getId()).append("</td></tr>");
        details.append("<tr><td><b>Author:</b></td><td>").append(selected.getAuthor()).append("</td></tr>");
        details.append("<tr><td><b>HOSTER:</b></td><td>").append(selected.getHoster()).append("</td></tr>");
        details.append("</table><hr>");

        details.append("<b>Text:</b><br>")
                .append("<div style='border:1px solid #ccc; padding:5px; margin-bottom:10px;'>")
                .append(toSafeHtml(selected.getText()))
                .append("</div><hr>");

        if (selected.getDiscogs() != null && !selected.getDiscogs().isEmpty()) {
            details.append("<b>Discogs Links:</b><br>");
            for (String link : selected.getDiscogs()) {
                details.append("  - <a href='").append(link).append("'>").append(link).append("</a><br>");
            }
            details.append("<hr>");
        }

        if (selected.getDownloadLinks() != null && !selected.getDownloadLinks().isEmpty()) {
            details.append("<b>Download Links:</b><br>");
            for (String link : selected.getDownloadLinks()) {
                details.append("  - <a href='").append(link).append("'>").append(link).append("</a><br>");
            }
            details.append("<hr>");
        }

        details.append("</body></html>");
        postDetailsArea.setText(details.toString());
        postDetailsArea.setCaretPosition(0);
    }

    private String toSafeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("\n", "<br>");
    }

    public static String cleanMessage(String rawText) {
        return rawText.replaceAll("(?s)(Cita de:.*?\\d{2}:\\d{2}:\\d{2} (am|pm)\\s*)+", "").trim();
    }

    private class PostListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof Post) {
                Post post = (Post) value;
                String displayText;

                if (post.getAlbumTitles() != null && !post.getAlbumTitles().isEmpty()) {
                    displayText = "<html><b> " + post.getAlbumTitles().get(0) + " </b></html>";
                } else if (post.getText() != null && !post.getText().isEmpty()) {
                    String cleanText = cleanMessage(post.getText());
                    if (cleanText.length() > 50)
                        cleanText = cleanText.substring(0, 50) + "...";
                    displayText = cleanText;
                } else {
                    displayText = "Post ID: " + post.getId();
                }
                setText(displayText);
            }

            setBorder(new EmptyBorder(5, 5, 5, 5));
            return this;
        }
    }
}
