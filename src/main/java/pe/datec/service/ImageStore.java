package pe.datec.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas de los enlaces de imagen. La base de datos guarda solo el enlace (ImagenProducto.imagen),
 * nunca el archivo: la imagen vive en el servidor web que la publica.
 */
public final class ImageStore {
  private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s\"'<>]+");

  private ImageStore() {}

  public static boolean isUrl(String text) {
    return text != null && text.length() <= 2048 && text.matches("(?i)https?://\\S+");
  }

  /** Primer enlace http(s) dentro de un texto (por ejemplo, lo que se arrastra desde el navegador). */
  public static String firstUrl(String text) {
    if (text == null) return null;
    Matcher m = URL.matcher(text);
    return m.find() ? m.group() : null;
  }

  /** Nombre corto para mostrar debajo de una miniatura: dominio y archivo. */
  public static String label(String url) {
    String rest = url.replaceFirst("(?i)^https?://", "");
    int slash = rest.indexOf('/');
    String host = slash > 0 ? rest.substring(0, slash) : rest;
    String file = slash > 0 ? rest.substring(rest.lastIndexOf('/') + 1) : "";
    int q = file.indexOf('?');
    if (q >= 0) file = file.substring(0, q);
    if (host.startsWith("www.")) host = host.substring(4);
    if (file.length() > 14) file = file.substring(0, 6) + "…" + file.substring(file.length() - 7);
    String text = file.isEmpty() ? host : host + " · " + file;
    return text.length() > 26 ? text.substring(0, 25) + "…" : text;
  }
}
