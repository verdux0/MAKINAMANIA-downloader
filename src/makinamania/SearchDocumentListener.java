package makinamania;

import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.Timer;

public class SearchDocumentListener implements DocumentListener {
    private final PostManager postManager;
    private final Timer debounceTimer;

    public SearchDocumentListener(PostManager postManager) {
        this.postManager = postManager;

        // Timer con 1 segundo de retraso
        debounceTimer = new Timer(1000, e -> postManager.applyCurrentFilter());
        debounceTimer.setRepeats(false);
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        restartDebounce();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        restartDebounce();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        restartDebounce();
    }

    private void restartDebounce() {
        debounceTimer.restart(); // cada vez que se escribe, reinicia el contador
    }
}
