package makinamania;

import makinamania.ui.DataPanel;
import makinamania.ui.ScrapingPanel;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MainApp extends JFrame {
    private JTabbedPane tabbedPane;
    private PostManager postManager;
    private DataPanel dataPanel;
    private ScrapingPanel scrapingPanel;

    public MainApp() {
        initializeUI();
    }

    private void initializeUI() {
        setTitle("MAKINAMANIA downloader");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1000, 700));

        // Paneles y managers
        dataPanel = new DataPanel(null);

        postManager = new PostManager(
                dataPanel.getListModel(),
                dataPanel.getPostCountLabel(),
                dataPanel.getSearchField());

        dataPanel.setPostManager(postManager);

        tabbedPane = new JTabbedPane();
        scrapingPanel = new ScrapingPanel(postManager, tabbedPane);

        tabbedPane.addTab("Scraping", scrapingPanel);
        tabbedPane.addTab("Data", dataPanel);

        add(tabbedPane, BorderLayout.CENTER);

        JPanel footer = createFooter();
        add(footer, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        ConsoleLogger.start("Application started successfully.");
    }

    private JPanel createFooter() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(5, 10, 5, 10));

        JLabel versionLabel = new JLabel("Version 2.0 | by: verduxo | por amor al ARTE, por amor a la MAKINA!!");
        JLabel profileLink = new JLabel("Mi Perfil!");
        profileLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        profileLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(java.net.URI.create(
                            "https://www.makinamania.net/index.php?action=profile;u=231357"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        panel.add(versionLabel, BorderLayout.WEST);

        // Right side: Toggle + Profile Link
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightPanel.setOpaque(false);

        JToggleButton themeToggle = new JToggleButton("Modo Oscuro");
        themeToggle.setSelected(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        themeToggle.addActionListener(e -> {
            try {
                if (themeToggle.isSelected()) {
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                } else {
                    UIManager.setLookAndFeel(new FlatLightLaf());
                }
                FlatLaf.updateUI();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        rightPanel.add(themeToggle);
        rightPanel.add(profileLink);

        panel.add(rightPanel, BorderLayout.EAST);
        return panel;
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            new MainApp().setVisible(true);
        });
    }
}