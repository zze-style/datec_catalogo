package pe.datec.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.*;

/**
 * Carga imágenes en segundo plano y las guarda en memoria, para que aparezcan sin congelar la
 * ventana. Las respuestas siempre se entregan en el hilo de Swing.
 *
 * <p>Tamaño normal (miniaturas, visor de la ficha): a las URL de Cloudinary se les pide
 * w_300,c_limit,f_jpg. Tamaño grande («Ver en grande»): f_jpg sin reducir. El formato JPG evita
 * las imágenes WebP, que Java no puede abrir. Las fotos del equipo pendientes de subir se leen
 * directamente del disco.
 */
public final class Thumbnails {
  private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
  private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
  private static final Map<String, List<Consumer<BufferedImage>>> WAITING = new HashMap<>();
  private static final ExecutorService POOL =
      Executors.newFixedThreadPool(
          3,
          r -> {
            Thread t = new Thread(r, "datec-imagenes");
            t.setDaemon(true);
            return t;
          });
  private static volatile CloudinaryStorageService cloud = CloudinaryStorageService.fromConfig();

  private Thumbnails() {}

  /** Vuelve a leer la configuración de Cloudinary (tras guardarla). */
  public static void reloadConfig() {
    cloud = CloudinaryStorageService.fromConfig();
  }

  public static void get(String path, Consumer<BufferedImage> ready) {
    request(path, false, ready);
  }

  public static void getLarge(String path, Consumer<BufferedImage> ready) {
    request(path, true, ready);
  }

  private static void request(String path, boolean large, Consumer<BufferedImage> ready) {
    String key = (large ? "L|" : "S|") + path;
    BufferedImage cached = CACHE.get(key);
    if (cached != null || FAILED.contains(key)) {
      ready.accept(cached);
      return;
    }
    synchronized (WAITING) {
      List<Consumer<BufferedImage>> list = WAITING.get(key);
      if (list != null) {
        list.add(ready);
        return;
      }
      WAITING.put(key, new ArrayList<>(List.of(ready)));
    }
    POOL.submit(
        () -> {
          BufferedImage image = null;
          try {
            image = fetch(path, large);
            CACHE.put(key, image);
          } catch (Exception e) {
            FAILED.add(key);
          }
          BufferedImage result = image;
          List<Consumer<BufferedImage>> list;
          synchronized (WAITING) {
            list = WAITING.remove(key);
          }
          SwingUtilities.invokeLater(() -> list.forEach(c -> c.accept(result)));
        });
  }

  private static BufferedImage fetch(String path, boolean large) throws Exception {
    int w = large ? 1600 : 800, h = large ? 1200 : 600;
    if (LocalImages.isPending(path)) return scale(LocalImages.read(new File(path)), w, h);
    CloudinaryStorageService c = cloud;
    return Images.load(large ? c.largeUrl(path) : c.thumbnailUrl(path), w, h);
  }

  private static BufferedImage scale(BufferedImage image, int maxW, int maxH) {
    double f = Math.min(1, Math.min((double) maxW / image.getWidth(), (double) maxH / image.getHeight()));
    int w = Math.max(1, (int) (image.getWidth() * f)), h = Math.max(1, (int) (image.getHeight() * f));
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, w, h);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(image, 0, 0, w, h, null);
    g.dispose();
    return out;
  }

  /** Reutiliza lo ya cargado de una foto local para su nueva URL (evita descargarla al guardar). */
  public static void alias(String from, String to) {
    for (String size : new String[] {"S|", "L|"}) {
      BufferedImage img = CACHE.get(size + from);
      if (img != null) CACHE.put(size + to, img);
    }
  }

  public static BufferedImage cached(String path) {
    return CACHE.get("S|" + path);
  }

  public static boolean failed(String path) {
    return FAILED.contains("S|" + path);
  }

  /** Reduce la imagen para que quepa en w × h sin deformarla. */
  public static ImageIcon fit(BufferedImage image, int w, int h) {
    double f = Math.min(1, Math.min((double) w / image.getWidth(), (double) h / image.getHeight()));
    int nw = Math.max(1, (int) (image.getWidth() * f)), nh = Math.max(1, (int) (image.getHeight() * f));
    BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(image, 0, 0, nw, nh, null);
    g.dispose();
    return new ImageIcon(out);
  }

  /** Recuadro gris con un texto, para imágenes que se están cargando o que no existen. */
  public static ImageIcon placeholder(int w, int h, String text) {
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(new Color(241, 245, 249));
    g.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
    g.setColor(new Color(203, 213, 225));
    g.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
    g.setColor(new Color(100, 116, 139));
    g.setFont(new Font("Segoe UI", Font.PLAIN, Math.max(11, Math.min(14, w / 14))));
    FontMetrics m = g.getFontMetrics();
    String[] lines = text.split("\n");
    int y = (h - lines.length * m.getHeight()) / 2 + m.getAscent();
    for (String line : lines) {
      g.drawString(line, (w - m.stringWidth(line)) / 2, y);
      y += m.getHeight();
    }
    g.dispose();
    return new ImageIcon(out);
  }
}
