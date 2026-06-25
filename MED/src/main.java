import javax.swing.*;

public class main {
    public static void main(String[] args) {
        // Use system look-and-feel for native buttons/scrollbars
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            AppWindow window = new AppWindow();
            window.setVisible(true);
            window.startLoading(); // loads GTFS in background thread
        });
    }
}
