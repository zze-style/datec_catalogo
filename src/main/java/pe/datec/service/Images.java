package pe.datec.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Descarga la imagen de un enlace para mostrarla, con límites de tiempo, bytes y dimensiones. La
 * imagen no se guarda en la base ni en disco: solo se muestra.
 */
public final class Images {
  private Images() {}

  /** Descarga y reduce la imagen para que quepa en maxW × maxH. */
  public static BufferedImage load(String location, int maxW, int maxH) throws IOException {
    if (!ImageStore.isUrl(location))
      throw new IOException("Solo se admiten enlaces que empiecen con http:// o https://.");
    byte[] bytes;
    URLConnection connection = URI.create(location).toURL().openConnection();
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(8000);
    // Algunos sitios rechazan clientes sin identificación de navegador.
    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0) DATEC-Catalogo");
    connection.setRequestProperty("Accept", "image/png,image/jpeg,image/*;q=0.8");
    try (InputStream in = connection.getInputStream()) {
      bytes = in.readNBytes(15_000_001);
    }
    if (bytes.length > 15_000_000) throw new IOException("La imagen supera 15 MB.");
    try (ImageInputStream stream =
        ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new IOException("Use una imagen PNG o JPEG válida.");
      ImageReader reader = readers.next();
      try {
        reader.setInput(stream);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if ((long) w * h > 25_000_000)
          throw new IOException("La imagen es demasiado grande (máximo 25 megapíxeles).");
        BufferedImage image = reader.read(0);
        double factor = Math.min(1, Math.min((double) maxW / w, (double) maxH / h));
        if (factor == 1) return image;
        BufferedImage scaled =
            new BufferedImage((int) (w * factor), (int) (h * factor), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
        g.dispose();
        return scaled;
      } finally {
        reader.dispose();
      }
    }
  }
}
