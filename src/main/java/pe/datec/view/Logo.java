package pe.datec.view;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Logotipo de DATÁLOGO dibujado con Java2D: una etiqueta de precio (catálogo de productos) con la
 * inicial de Datec, sobre un cuadrado azul con esquinas redondeadas. Al dibujarse por código se ve
 * nítido en cualquier tamaño y no depende de archivos externos.
 */
public final class Logo {
  private static final Color TOP = new Color(58, 128, 214), BOTTOM = new Color(28, 74, 146);

  private Logo() {}

  /** Dibuja el logotipo dentro de un cuadrado de lado {@code s} con origen en (0, 0). */
  public static void paint(Graphics2D g, int s) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

    // Fondo: cuadrado azul con degradado vertical.
    double arc = s * 0.28;
    g.setPaint(new GradientPaint(0, 0, TOP, 0, s, BOTTOM));
    g.fill(new RoundRectangle2D.Double(0, 0, s, s, arc, arc));

    // Etiqueta de precio en diagonal: la punta queda arriba a la izquierda.
    AffineTransform original = g.getTransform();
    g.rotate(Math.PI / 4, s / 2.0, s / 2.0);
    Path2D tag = new Path2D.Double();
    double r = s * 0.06; // radio de las esquinas del cuerpo
    tag.moveTo(s * 0.16, s * 0.50);
    tag.lineTo(s * 0.35, s * 0.29);
    tag.lineTo(s * 0.84 - r, s * 0.29);
    tag.quadTo(s * 0.84, s * 0.29, s * 0.84, s * 0.29 + r);
    tag.lineTo(s * 0.84, s * 0.71 - r);
    tag.quadTo(s * 0.84, s * 0.71, s * 0.84 - r, s * 0.71);
    tag.lineTo(s * 0.35, s * 0.71);
    tag.closePath();
    g.setColor(new Color(0, 0, 0, 45)); // sombra suave
    g.fill(AffineTransform.getTranslateInstance(s * 0.012, s * 0.03).createTransformedShape(tag));
    g.setColor(Color.WHITE);
    g.fill(tag);
    // Orificio de la etiqueta.
    double hole = s * 0.085;
    g.setPaint(new GradientPaint(0, 0, TOP, 0, s, BOTTOM));
    g.fill(new Ellipse2D.Double(s * 0.33 - hole / 2, s * 0.50 - hole / 2, hole, hole));
    g.setTransform(original);

    // Inicial "D" sobre el cuerpo de la etiqueta, sin rotar para que se lea con claridad.
    Font font = new Font("Segoe UI", Font.BOLD, Math.max(6, (int) Math.round(s * 0.34)));
    g.setFont(font);
    FontMetrics fm = g.getFontMetrics();
    double cx = s * 0.565, cy = s * 0.565; // centro del cuerpo de la etiqueta ya rotado
    float x = (float) (cx - fm.stringWidth("D") / 2.0);
    float y = (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0);
    g.setColor(BOTTOM);
    g.drawString("D", x, y);
  }

  /** Imagen del logotipo con fondo transparente. */
  public static BufferedImage image(int size) {
    BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    paint(g, size);
    g.dispose();
    return img;
  }

  /** Icono de Swing para etiquetas y botones. */
  public static Icon icon(int size) {
    return new ImageIcon(image(size));
  }

  /** Tamaños que Windows usa en la barra de título, la barra de tareas y Alt+Tab. */
  public static List<Image> windowImages() {
    List<Image> images = new ArrayList<>();
    for (int size : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256}) images.add(image(size));
    return images;
  }
}
