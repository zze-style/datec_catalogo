package pe.datec;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import pe.datec.controller.AppController;
import pe.datec.view.*;

public final class App {
  private App() {}

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          Styles.install();
          MainView view = new MainView();
          AppController controller = new AppController(view);
          JFrame frame = new JFrame("DATÁLOGO - Catálogo de productos");
          frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
          frame.setIconImages(Logo.windowImages());
          frame.setContentPane(view);
          JPanel glass = new JPanel();
          glass.setOpaque(false);
          glass.addMouseListener(new MouseAdapter() {});
          glass.addMouseMotionListener(new MouseMotionAdapter() {});
          glass.setFocusTraversalKeysEnabled(false);
          frame.setGlassPane(glass);
          KeyEventDispatcher dispatcher = event -> glass.isVisible();
          KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
          frame.addWindowListener(
              new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                  if (!glass.isVisible() && controller.products.confirmLeave()) {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .removeKeyEventDispatcher(dispatcher);
                    frame.dispose();
                  }
                }
              });
          frame.setMinimumSize(new Dimension(1050, 720));
          frame.pack();
          frame.setLocationRelativeTo(null);
          frame.setVisible(true);
        });
  }
}
