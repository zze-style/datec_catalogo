package pe.datec.view;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import pe.datec.service.*;

/**
 * Visor de imágenes de la ficha: muestra la imagen directamente, con flechas para pasar a la
 * siguiente y clic para verla en tamaño completo. Reemplaza la lista de rutas anterior.
 */
public final class ImageViewer extends JPanel {
  private final int width, height;
  private final JLabel picture = new JLabel("", SwingConstants.CENTER);
  private final JButton previous = Styles.button("‹", false), next = Styles.button("›", false);
  private final JLabel counter = Styles.muted(" ");
  private List<String> paths = List.of();
  private int index;
  private boolean large;

  public ImageViewer(int width, int height) {
    super(new BorderLayout(0, 6));
    this.width = width;
    this.height = height;
    setOpaque(false);
    picture.setPreferredSize(new Dimension(width, height));
    picture.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    picture.setToolTipText("Clic para ver en tamaño completo");
    picture.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            enlarge();
          }
        });
    previous.addActionListener(e -> move(-1));
    next.addActionListener(e -> move(1));
    for (JButton b : new JButton[] {previous, next}) {
      b.setBorder(new CompoundBorder(new LineBorder(Styles.LINE), new EmptyBorder(3, 12, 3, 12)));
      b.setFont(b.getFont().deriveFont(Font.BOLD, 16f));
    }
    counter.setHorizontalAlignment(SwingConstants.CENTER);
    JPanel bar = new JPanel(new BorderLayout());
    bar.setOpaque(false);
    bar.add(previous, BorderLayout.WEST);
    bar.add(counter);
    bar.add(next, BorderLayout.EAST);
    add(picture);
    add(bar, BorderLayout.SOUTH);
    setImages(List.of());
  }

  public void setImages(List<String> images) {
    paths = images == null ? List.of() : List.copyOf(images);
    index = 0;
    refresh();
  }

  private void move(int delta) {
    if (paths.isEmpty()) return;
    index = (index + delta + paths.size()) % paths.size();
    refresh();
  }

  private void refresh() {
    previous.setEnabled(paths.size() > 1);
    next.setEnabled(paths.size() > 1);
    if (paths.isEmpty()) {
      picture.setIcon(Thumbnails.placeholder(width, height, "Producto sin imágenes"));
      counter.setText("Sin imágenes registradas");
      return;
    }
    String path = paths.get(index);
    counter.setText("Imagen " + (index + 1) + " de " + paths.size());
    picture.setIcon(Thumbnails.placeholder(width, height, "Cargando imagen…"));
    java.util.function.Consumer<java.awt.image.BufferedImage> show =
        image -> {
          if (index < paths.size() && paths.get(index).equals(path))
            picture.setIcon(
                image == null
                    ? Thumbnails.placeholder(width, height, "No se encontró la imagen\n" + ImageStore.label(path))
                    : Thumbnails.fit(image, width, height));
        };
    if (large) Thumbnails.getLarge(path, show);
    else Thumbnails.get(path, show);
  }

  private void enlarge() {
    if (paths.isEmpty()) return;
    showLarge(this, paths, index);
  }

  /** Ventana de imagen grande con navegación. También la usa la galería del formulario. */
  public static void showLarge(Component parent, List<String> paths, int start) {
    ImageViewer large = new ImageViewer(780, 560);
    large.paths = List.copyOf(paths);
    large.large = true;
    large.index = start;
    large.picture.setCursor(Cursor.getDefaultCursor());
    large.picture.setToolTipText(null);
    for (MouseListener l : large.picture.getMouseListeners()) large.picture.removeMouseListener(l);
    large.refresh();
    JOptionPane.showMessageDialog(parent, large, "Imágenes del producto", JOptionPane.PLAIN_MESSAGE);
  }
}
