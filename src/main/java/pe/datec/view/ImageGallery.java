package pe.datec.view;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import pe.datec.service.*;

/**
 * Galería de imágenes del formulario de producto. La base de datos guarda solo enlaces (URL) en
 * ImagenProducto, nunca el archivo. Las fotos del equipo quedan PENDIENTES (solo vista previa local)
 * y se suben a Cloudinary recién al pulsar «Guardar cambios»; también se puede pegar un enlace o
 * arrastrar una imagen desde el navegador. El orden de la galería es el orden en que se guardan.
 */
public final class ImageGallery extends JPanel {
  private static final int THUMB_W = 132, THUMB_H = 96;
  public final JButton upload = Styles.button("Agregar desde el equipo…", true),
      addUrl = Styles.button("Agregar enlace…", false),
      hostKey = Styles.button("Configurar Cloudinary…", false),
      remove = Styles.button("Quitar imagen", false),
      enlarge = Styles.button("Ver en grande", false);
  private final DefaultListModel<String> model = new DefaultListModel<>();
  private final JList<String> list = new JList<>(model);
  private final JLabel preview = new JLabel("", SwingConstants.CENTER);
  private final JTextArea location = new JTextArea(3, 20);
  private final JLabel count = Styles.muted(" ");
  private final CardLayout cards = new CardLayout();
  private final JPanel area = new JPanel(cards);

  public ImageGallery() {
    super(new BorderLayout(16, 12));
    setBorder(new EmptyBorder(18, 18, 18, 18));
    JLabel help =
        Styles.muted(
            "<html>Las fotos del equipo quedan <b>pendientes</b> y se suben a Cloudinary al pulsar"
                + " «Guardar cambios»; la base guarda solo su <b>enlace</b>. También puede pegar el"
                + " enlace de una imagen de internet. Opcionales; se recomiendan 3 por producto.</html>");
    add(help, BorderLayout.NORTH);

    list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
    list.setVisibleRowCount(-1);
    list.setFixedCellWidth(THUMB_W + 22);
    list.setFixedCellHeight(THUMB_H + 40);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new ThumbRenderer());
    list.addListSelectionListener(e -> showSelected());
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) openLarge();
          }
        });
    list.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_DELETE) removeSelected();
            if (e.getKeyCode() == KeyEvent.VK_V && e.isControlDown()) pasteUrl();
          }
        });

    JLabel empty =
        new JLabel(
            "<html><div style='text-align:center'><b>Sin imágenes</b><br><br>Arrastre aquí fotos del"
                + " equipo o imágenes del navegador<br>o pulse «Subir desde el equipo»</div></html>",
            SwingConstants.CENTER);
    empty.setForeground(Styles.MUTED);
    JPanel emptyPanel = new JPanel(new BorderLayout());
    emptyPanel.setBorder(
        BorderFactory.createDashedBorder(Styles.LINE.darker(), 2f, 8f, 5f, true));
    emptyPanel.setBackground(Styles.BG);
    emptyPanel.add(empty);
    area.add(emptyPanel, "empty");
    area.add(Styles.scroll(list), "list");
    DropFiles drop = new DropFiles();
    list.setTransferHandler(drop);
    emptyPanel.setTransferHandler(drop);
    area.setTransferHandler(drop);

    JPanel side = new JPanel(new BorderLayout(0, 10));
    side.setPreferredSize(new Dimension(300, 0));
    preview.setPreferredSize(new Dimension(300, 220));
    location.setEditable(false);
    location.setLineWrap(true);
    location.setOpaque(false);
    location.setForeground(Styles.MUTED);
    location.setFont(location.getFont().deriveFont(12f));
    side.add(preview, BorderLayout.NORTH);
    side.add(location);
    JPanel sideButtons = new JPanel(new GridLayout(0, 1, 0, 8));
    sideButtons.add(enlarge);
    sideButtons.add(remove);
    side.add(sideButtons, BorderLayout.SOUTH);

    JPanel center = new JPanel(new BorderLayout(0, 10));
    center.add(area);
    center.add(Styles.row(upload, addUrl, hostKey, count), BorderLayout.SOUTH);
    add(center);
    add(side, BorderLayout.EAST);

    addUrl.addActionListener(e -> askUrl());
    upload.addActionListener(e -> chooseFiles());
    hostKey.addActionListener(e -> askCloudinary());
    remove.addActionListener(e -> removeSelected());
    enlarge.addActionListener(e -> openLarge());
    refresh();
  }

  public void setPaths(List<String> paths) {
    model.clear();
    if (paths != null) paths.forEach(model::addElement);
    if (!model.isEmpty()) list.setSelectedIndex(0);
    refresh();
  }

  public List<String> paths() {
    return Collections.list(model.elements());
  }

  private void add(String url) {
    if (!model.contains(url)) model.addElement(url);
    list.setSelectedValue(url, true);
    refresh();
  }

  /** Pide el enlace, muestra la imagen y solo la agrega si el usuario la confirma. */
  private void askUrl() {
    askUrl(clipboardUrl());
  }

  private void askUrl(String initial) {
    JTextField field = Styles.input(42);
    if (initial != null) field.setText(initial);
    JLabel image = new JLabel(Thumbnails.placeholder(320, 220, "Pegue el enlace y pulse Ver"), SwingConstants.CENTER);
    JLabel state = Styles.muted(" ");
    JButton check = Styles.button("Ver", false);
    boolean[] works = {false};
    Runnable load =
        () -> {
          String url = field.getText().trim();
          works[0] = false;
          if (!ImageStore.isUrl(url)) {
            image.setIcon(Thumbnails.placeholder(320, 220, "Escriba un enlace http:// o https://"));
            state.setText(" ");
            return;
          }
          image.setIcon(Thumbnails.placeholder(320, 220, "Cargando…"));
          state.setText("Descargando la vista previa…");
          Thumbnails.get(
              url,
              img -> {
                if (!url.equals(field.getText().trim())) return;
                works[0] = img != null;
                image.setIcon(
                    img == null
                        ? Thumbnails.placeholder(320, 220, "No se pudo abrir\nla imagen de este enlace")
                        : Thumbnails.fit(img, 320, 220));
                state.setText(
                    img == null
                        ? "<html>Puede ser el enlace de una página y no de la imagen, o un formato"
                            + " no admitido (use PNG o JPG).</html>"
                        : "Imagen correcta.");
              });
        };
    check.addActionListener(e -> load.run());
    field.addActionListener(e -> load.run());
    JPanel row = new JPanel(new BorderLayout(8, 0));
    row.add(field);
    row.add(check, BorderLayout.EAST);
    JPanel form = new JPanel(new BorderLayout(0, 10));
    form.add(Styles.field("Enlace (URL) de la imagen", row), BorderLayout.NORTH);
    form.add(image);
    form.add(state, BorderLayout.SOUTH);
    form.setPreferredSize(new Dimension(460, 340));
    if (initial != null) load.run();
    while (JOptionPane.showConfirmDialog(
            this, form, "Agregar enlace de imagen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION) {
      String url = field.getText().trim();
      if (!ImageStore.isUrl(url)) {
        JOptionPane.showMessageDialog(this, "Escriba un enlace que empiece con http:// o https://.");
        continue;
      }
      if (!works[0]
          && JOptionPane.showConfirmDialog(
                  this,
                  "No se comprobó que el enlace muestre una imagen. ¿Agregarlo de todos modos?",
                  "Enlace sin vista previa",
                  JOptionPane.YES_NO_OPTION)
              != JOptionPane.YES_OPTION) continue;
      add(url);
      return;
    }
  }

  private void pasteUrl() {
    String url = clipboardUrl();
    if (url != null) askUrl(url);
  }

  private static String clipboardUrl() {
    try {
      Object data =
          Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
      return ImageStore.firstUrl((String) data);
    } catch (Exception e) {
      return null;
    }
  }

  /** Credenciales de Cloudinary, guardadas en config/cloudinary.properties (no en la base). */
  private boolean askCloudinary() {
    java.util.Properties p = CloudinaryStorageService.loadFile();
    JTextField cloud = Styles.input(26), preset = Styles.input(26), key = Styles.input(26);
    JPasswordField secret = new JPasswordField(26);
    secret.setMargin(new Insets(7, 8, 7, 8));
    cloud.setText(p.getProperty("cloudinary.cloud_name", ""));
    preset.setText(p.getProperty("cloudinary.upload_preset", ""));
    key.setText(p.getProperty("cloudinary.api_key", ""));
    secret.setText(p.getProperty("cloudinary.api_secret", ""));
    JPanel form = new JPanel(new GridLayout(0, 1, 0, 10));
    form.add(
        Styles.muted(
            "<html>Datos de la cuenta de Cloudinary (Dashboard → API Keys). Se guardan solo en este"
                + " equipo,<br>en config/cloudinary.properties, nunca en la base de datos.</html>"));
    form.add(Styles.field("Cloud name *", cloud));
    form.add(Styles.field("Upload preset sin firma (recomendado para subir)", preset));
    form.add(Styles.field("API key (para borrar fotos quitadas)", key));
    form.add(Styles.field("API secret (para borrar fotos quitadas)", secret));
    if (JOptionPane.showConfirmDialog(this, form, "Configurar Cloudinary", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        != JOptionPane.OK_OPTION) return false;
    try {
      char[] s = secret.getPassword();
      CloudinaryStorageService.saveFile(cloud.getText(), preset.getText(), key.getText(), new String(s));
      Arrays.fill(s, '\0');
      Thumbnails.reloadConfig();
      CloudinaryStorageService c = CloudinaryStorageService.fromConfig();
      if (!c.canUpload())
        JOptionPane.showMessageDialog(this, "Falta el cloud name y un upload preset (o API key y secret) para subir fotos.");
      return c.canUpload();
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this, "No se pudo guardar la configuración: " + ex.getMessage());
      return false;
    }
  }

  private void chooseFiles() {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Elija las fotos del producto");
    chooser.setMultiSelectionEnabled(true);
    chooser.setAcceptAllFileFilterUsed(false);
    chooser.setFileFilter(
        new javax.swing.filechooser.FileNameExtensionFilter("Imágenes PNG, JPG o GIF", "png", "jpg", "jpeg", "gif"));
    chooser.setAccessory(new ChooserPreview(chooser));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
      addLocal(Arrays.asList(chooser.getSelectedFiles()));
  }

  /** Agrega fotos del equipo como pendientes: no se suben hasta «Guardar cambios». */
  private void addLocal(List<File> files) {
    List<String> errors = new ArrayList<>();
    for (File f : files) {
      if (!LocalImages.isImageFile(f)) errors.add(f.getName() + ": use una imagen PNG, JPG o GIF.");
      else if (f.length() > 30_000_000) errors.add(f.getName() + ": la imagen supera 30 MB.");
      else add(f.getAbsolutePath());
    }
    if (!errors.isEmpty())
      JOptionPane.showMessageDialog(this, String.join("\n", errors), "Algunas fotos no se agregaron", JOptionPane.WARNING_MESSAGE);
    if (!files.isEmpty() && !CloudinaryStorageService.fromConfig().canUpload())
      JOptionPane.showMessageDialog(
          this,
          "Las fotos quedaron pendientes. Para poder guardarlas, configure Cloudinary\n(botón «Configurar Cloudinary…»).",
          "Falta configurar Cloudinary",
          JOptionPane.INFORMATION_MESSAGE);
  }

  private void removeSelected() {
    int i = list.getSelectedIndex();
    if (i < 0) return;
    model.remove(i);
    if (!model.isEmpty()) list.setSelectedIndex(Math.min(i, model.size() - 1));
    refresh();
  }

  private void openLarge() {
    int i = list.getSelectedIndex();
    if (i >= 0) ImageViewer.showLarge(this, paths(), i);
  }

  private void refresh() {
    cards.show(area, model.isEmpty() ? "empty" : "list");
    int n = model.size();
    long pending = paths().stream().filter(LocalImages::isPending).count();
    count.setText(
        n == 0
            ? "Sin imágenes"
            : n + (n == 1 ? " imagen" : " imágenes") + (pending > 0 ? " · " + pending + " pendiente(s) de subir" : "")
                + (n < 3 ? " · se recomiendan 3" : ""));
    showSelected();
  }

  private void showSelected() {
    String path = list.getSelectedValue();
    remove.setEnabled(path != null);
    enlarge.setEnabled(path != null);
    if (path == null) {
      preview.setIcon(Thumbnails.placeholder(300, 220, "Seleccione una miniatura"));
      location.setText("");
      return;
    }
    location.setText(
        LocalImages.isPending(path)
            ? "Pendiente: se subirá a Cloudinary al pulsar «Guardar cambios».\n" + path
            : "Enlace que se guarda en la base:\n" + path);
    preview.setIcon(Thumbnails.placeholder(300, 220, "Cargando…"));
    Thumbnails.get(
        path,
        image -> {
          if (path.equals(list.getSelectedValue()))
            preview.setIcon(
                image == null
                    ? Thumbnails.placeholder(300, 220, "No se pudo abrir la imagen")
                    : Thumbnails.fit(image, 300, 220));
        });
  }

  /** Miniatura con el nombre del archivo debajo. */
  private final class ThumbRenderer extends JLabel implements ListCellRenderer<String> {
    ThumbRenderer() {
      setHorizontalAlignment(CENTER);
      setVerticalTextPosition(BOTTOM);
      setHorizontalTextPosition(CENTER);
      setIconTextGap(6);
      setOpaque(true);
      setFont(getFont().deriveFont(12f));
    }

    @Override
    public Component getListCellRendererComponent(
        JList<? extends String> l, String path, int i, boolean selected, boolean focus) {
      var image = Thumbnails.cached(path);
      if (image != null) setIcon(Thumbnails.fit(image, THUMB_W, THUMB_H));
      else if (Thumbnails.failed(path)) setIcon(Thumbnails.placeholder(THUMB_W, THUMB_H, "No disponible"));
      else {
        setIcon(Thumbnails.placeholder(THUMB_W, THUMB_H, "Cargando…"));
        Thumbnails.get(path, ignored -> list.repaint());
      }
      boolean pending = LocalImages.isPending(path);
      String label = pending ? "Pendiente · " + new File(path).getName() : ImageStore.label(path);
      if (label.length() > 26) label = label.substring(0, 25) + "…";
      setText((i == 0 ? "★ " : "") + label);
      setToolTipText((i == 0 ? "Imagen principal · " : "") + (pending ? "Se subirá a Cloudinary al guardar: " : "") + path);
      setBackground(selected ? Styles.SELECTED : Color.WHITE);
      setForeground(Styles.INK);
      setBorder(
          new CompoundBorder(
              new EmptyBorder(4, 4, 4, 4),
              new LineBorder(selected ? Styles.BLUE : Color.WHITE, 2, true)));
      return this;
    }
  }

  /**
   * Acepta fotos arrastradas desde el explorador (quedan pendientes de subir a Cloudinary) o una
   * imagen arrastrada desde el navegador (llega como texto con su enlace).
   */
  private final class DropFiles extends TransferHandler {
    @Override
    public boolean canImport(TransferSupport s) {
      return s.isDataFlavorSupported(DataFlavor.stringFlavor)
          || s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public boolean importData(TransferSupport s) {
      try {
        if (!s.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && s.isDataFlavorSupported(DataFlavor.stringFlavor)) {
          String url = ImageStore.firstUrl((String) s.getTransferable().getTransferData(DataFlavor.stringFlavor));
          if (url != null) {
            SwingUtilities.invokeLater(() -> askUrl(url));
            return true;
          }
        }
        if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
          @SuppressWarnings("unchecked")
          List<File> dropped = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
          List<File> images = new ArrayList<>();
          for (File f : dropped) if (LocalImages.isImageFile(f)) images.add(f);
          if (!images.isEmpty()) {
            SwingUtilities.invokeLater(() -> addLocal(images));
            return true;
          }
        }
        return false;
      } catch (Exception e) {
        return false;
      }
    }
  }

  /** Vista previa dentro del selector de archivos: se ve la foto antes de elegirla. */
  private static final class ChooserPreview extends JPanel {
    private final JLabel image = new JLabel("", SwingConstants.CENTER);
    private final JLabel name = Styles.muted(" ");
    private File current;

    ChooserPreview(JFileChooser chooser) {
      super(new BorderLayout(0, 6));
      setBorder(new EmptyBorder(0, 12, 0, 0));
      setPreferredSize(new Dimension(230, 200));
      image.setIcon(Thumbnails.placeholder(220, 165, "Vista previa"));
      name.setHorizontalAlignment(SwingConstants.CENTER);
      add(image);
      add(name, BorderLayout.SOUTH);
      chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY, e -> show((File) e.getNewValue()));
    }

    private void show(File f) {
      current = f;
      if (f == null || !LocalImages.isImageFile(f)) {
        image.setIcon(Thumbnails.placeholder(220, 165, "Vista previa"));
        name.setText(" ");
        return;
      }
      name.setText(f.getName());
      image.setIcon(Thumbnails.placeholder(220, 165, "Cargando…"));
      new SwingWorker<java.awt.image.BufferedImage, Void>() {
        protected java.awt.image.BufferedImage doInBackground() throws Exception {
          return LocalImages.read(f);
        }

        protected void done() {
          if (current != f) return;
          try {
            image.setIcon(Thumbnails.fit(get(), 220, 165));
          } catch (Exception ex) {
            image.setIcon(Thumbnails.placeholder(220, 165, "No se puede abrir"));
          }
        }
      }.execute();
    }
  }
}
