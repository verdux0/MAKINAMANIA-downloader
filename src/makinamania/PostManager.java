package makinamania;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class PostManager {
    private List<Post> allPosts = new ArrayList<>();
    private Set<String> existingIds = new HashSet<>();
    private DefaultListModel<Post> listModel;
    private JLabel postCountLabel;
    private JTextField searchField;

    public PostManager(DefaultListModel<Post> listModel, JLabel postCountLabel, JTextField searchField) {
        this.listModel = listModel;
        this.postCountLabel = postCountLabel;
        this.searchField = searchField;
    }

    // Método principal para cargar posts en la lista
    public void loadPostsIntoList(List<Post> posts) {
        updatePosts(posts);
    }

    public void updatePosts(List<Post> newPosts) {
        allPosts.clear();
        existingIds.clear();
        if (newPosts != null) {
            for (Post p : newPosts) {
                if (p.getId() != null) {
                    existingIds.add(p.getId());
                }
                allPosts.add(p);
            }
        }
        applyCurrentFilter();
    }

    public void addNewPosts(List<Post> newPosts) {
        if (newPosts == null || newPosts.isEmpty())
            return;

        List<Post> toAdd = new ArrayList<>();
        for (Post p : newPosts) {
            if (p.getId() != null && !existingIds.contains(p.getId())) {
                existingIds.add(p.getId());
                toAdd.add(p);
            }
        }

        if (!toAdd.isEmpty()) {
            allPosts.addAll(toAdd);
            applyCurrentFilter();
        }
    }

    public void replaceAllPosts(List<Post> newPosts) {
        updatePosts(newPosts);
    }

    // Aplicar filtros basados en búsqueda y hoster
    public void applyCurrentFilter() {
        String searchText = searchField.getText().trim().toLowerCase();

        List<Post> filteredPosts = allPosts.stream()
                .filter(post -> searchText.isEmpty() || matchesSearch(post, searchText))
                .collect(Collectors.toList());

        updateListModel(filteredPosts);
    }

    // Filtrar por hoster específico
    public void filterPostsByHoster(String hoster) {
        String searchText = searchField.getText().trim().toLowerCase();

        List<Post> filteredPosts = allPosts.stream()
                .filter(post -> searchText.isEmpty() || matchesSearch(post, searchText))
                .filter(post -> hoster == null || hoster.isEmpty() ||
                        (post.getHoster() != null && post.getHoster().equalsIgnoreCase(hoster)))
                .collect(Collectors.toList());

        updateListModel(filteredPosts);
    }

    // Actualizar el modelo de lista visual (Thread Safe)
    private void updateListModel(List<Post> postsToShow) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (Post post : postsToShow) {
                listModel.addElement(post);
            }
            postCountLabel.setText("Posts: " + listModel.size() + " / " + allPosts.size());
        });
    }

    // Búsqueda en diferentes campos del post
    private boolean matchesSearch(Post post, String searchText) {
        // Buscar en el autor
        if (post.getAuthor() != null && post.getAuthor().toLowerCase().contains(searchText)) {
            return true;
        }

        // Buscar en las referencias
        if (post.getReference() != null && post.getReference().toLowerCase().contains(searchText)) {
            return true;
        }

        // Buscar en los títulos de álbumes
        if (post.getAlbumTitles() != null) {
            for (String title : post.getAlbumTitles()) {
                if (title.toLowerCase().contains(searchText)) {
                    return true;
                }
            }
        }

        // Buscar en enlaces de Discogs
        if (post.getDiscogs() != null) {
            for (String discog : post.getDiscogs()) {
                if (discog.toLowerCase().contains(searchText)) {
                    return true;
                }
            }
        }

        // Buscar en enlaces de descarga
        if (post.getDownloadLinks() != null) {
            for (String link : post.getDownloadLinks()) {
                if (link.toLowerCase().contains(searchText)) {
                    return true;
                }
            }
        }

        return false;
    }

    // Eliminar posts seleccionados
    public void deleteSelectedPosts(JList<Post> postsList) throws Exception {
        int[] indices = postsList.getSelectedIndices();
        if (indices.length == 0)
            return;

        // Eliminar de listModel y allPosts
        List<Post> toRemove = new ArrayList<>();
        // Collect items to remove first to avoid index issues
        for (int i : indices) {
            // Note: indices from getSelectedIndices are sorted ascending
            // But listModel might change if we remove one by one?
            // Better to get objects from listModel first
            if (i >= 0 && i < listModel.size()) {
                toRemove.add(listModel.get(i));
            }
        }

        // Remove from allPosts and existingIds
        for (Post p : toRemove) {
            allPosts.remove(p);
            if (p.getId() != null) {
                existingIds.remove(p.getId());
            }
        }

        // Update UI
        applyCurrentFilter();

        // Persistir en JSON
        savePostsToJson();
    }

    // Guardar los posts actuales en el JSON
    private void savePostsToJson() throws Exception {
        JsonUtils.saveAllPosts(allPosts);
    }

    // Getters
    public List<Post> getAllPosts() {
        return new ArrayList<>(allPosts);
    }

    public int getTotalPostsCount() {
        return allPosts.size();
    }

    public int getFilteredPostsCount() {
        return listModel.size();
    }

    // Limpiar todos los posts
    public void clearAllPosts() {
        allPosts.clear();
        existingIds.clear();
        updateListModel(new ArrayList<>());
    }
}