package pe.datec.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

/** Fotos del equipo pendientes de subir: lectura, vista previa y preparación (JPEG reducido). */
public final class LocalImages {
  private static final int MAX_SIDE = 1600;

  private LocalImages() {}

  public static boolean isImageFile(File f) {
    String n = f.getName().toLowerCase(Locale.ROOT);
    return f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif"));
  }

  /** Una entrada de la galería que todavía es un archivo del equipo (no un enlace). */
  public static boolean isPending(String entry) {
    return entry != null && !ImageStore.isUrl(entry);
  }

  public static BufferedImage read(File f) throws IOException {
    if (!isImageFile(f)) throw new IOException(f.getName() + ": use una imagen PNG, JPG o GIF.");
    if (f.length() > 30_000_000) throw new IOException(f.getName() + ": la imagen supera 30 MB.");
    BufferedImage image = ImageIO.read(f);
    if (image == null) throw new IOException(f.getName() + ": no se pudo leer la imagen.");
    return image;
  }

  /** Reduce la foto (lado mayor 1600 px) y la convierte a JPEG con fondo blanco. */
  public static byte[] prepareJpeg(File f) throws IOException {
    BufferedImage image = read(f);
    double k = Math.min(1, (double) MAX_SIDE / Math.max(image.getWidth(), image.getHeight()));
    int w = Math.max(1, (int) Math.round(image.getWidth() * k)),
        h = Math.max(1, (int) Math.round(image.getHeight() * k));
    BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = rgb.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, w, h);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.drawImage(image, 0, 0, w, h, null);
    g.dispose();
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
    ImageWriter writer = writers.next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(out);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.85f);
      writer.write(null, new IIOImage(rgb, null, null), param);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }
}
