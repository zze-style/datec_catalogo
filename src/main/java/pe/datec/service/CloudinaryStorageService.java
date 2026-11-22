package pe.datec.service;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alojamiento de las fotos de productos en Cloudinary, usando su API REST con las clases de Java
 * (sin SDK ni librerías externas). La base de datos guarda solo la secure_url en
 * ImagenProducto.imagen; el public_id se obtiene de esa misma URL, por eso no hace falta ninguna
 * columna nueva.
 *
 * <p>Credenciales: config/cloudinary.properties (excluido del repositorio) o variables de entorno
 * CLOUDINARY_CLOUD_NAME, CLOUDINARY_UPLOAD_PRESET, CLOUDINARY_API_KEY y CLOUDINARY_API_SECRET.
 * Para subir basta cloud_name + upload_preset (preset sin firma) o cloud_name + api_key +
 * api_secret. Para borrar (destroy) se necesitan api_key y api_secret.
 */
public final class CloudinaryStorageService {
  /** Error de Cloudinary con un mensaje listo para mostrar al usuario. */
  public static final class CloudinaryException extends IOException {
    public CloudinaryException(String message) {
      super(message);
    }
  }

  /** Resultado de una subida. */
  public record Uploaded(String secureUrl, String publicId) {}

  private static final Pattern FIELD = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");
  private final String cloudName, uploadPreset, apiKey, apiSecret, apiBase, deliveryBase;

  public CloudinaryStorageService(Properties p) {
    cloudName = value(p, "cloudinary.cloud_name", "CLOUDINARY_CLOUD_NAME");
    uploadPreset = value(p, "cloudinary.upload_preset", "CLOUDINARY_UPLOAD_PRESET");
    apiKey = value(p, "cloudinary.api_key", "CLOUDINARY_API_KEY");
    apiSecret = value(p, "cloudinary.api_secret", "CLOUDINARY_API_SECRET");
    apiBase = strip(p.getProperty("cloudinary.api_base", "https://api.cloudinary.com/v1_1"));
    deliveryBase = strip(p.getProperty("cloudinary.delivery_base", "https://res.cloudinary.com"));
  }

  /** Lee config/cloudinary.properties (si existe) junto a la aplicación. */
  public static CloudinaryStorageService fromConfig() {
    return new CloudinaryStorageService(loadFile());
  }

  public static Path configFile() {
    return AppPaths.home().resolve("config/cloudinary.properties");
  }

  public static Properties loadFile() {
    Properties p = new Properties();
    if (Files.exists(configFile()))
      try (Reader in = Files.newBufferedReader(configFile(), StandardCharsets.UTF_8)) {
        p.load(in);
      } catch (IOException ignored) {
      }
    return p;
  }

  /** Guarda las credenciales escritas en la pantalla de configuración. */
  public static void saveFile(String cloud, String preset, String key, String secret) throws IOException {
    Properties p = loadFile();
    put(p, "cloudinary.cloud_name", cloud);
    put(p, "cloudinary.upload_preset", preset);
    put(p, "cloudinary.api_key", key);
    put(p, "cloudinary.api_secret", secret);
    Files.createDirectories(configFile().getParent());
    try (Writer out = Files.newBufferedWriter(configFile(), StandardCharsets.UTF_8)) {
      p.store(out, "Credenciales de Cloudinary de DATEC. No subir al repositorio ni compartir.");
    }
  }

  private static void put(Properties p, String k, String v) {
    if (v == null || v.isBlank()) p.remove(k);
    else p.setProperty(k, v.trim());
  }

  private static String value(Properties p, String prop, String env) {
    String e = System.getenv(env);
    if (e != null && !e.isBlank()) return e.trim();
    return p.getProperty(prop, "").trim();
  }

  private static String strip(String s) {
    s = s.trim();
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  public boolean canUpload() {
    return !cloudName.isEmpty() && (!uploadPreset.isEmpty() || canSign());
  }

  public boolean canDelete() {
    return !cloudName.isEmpty() && canSign();
  }

  private boolean canSign() {
    return !apiKey.isEmpty() && !apiSecret.isEmpty();
  }

  // ------------------------------------------------------------------ subir

  /** Sube la foto (reducida a JPEG) a la carpeta productos/&lt;codigo&gt;/. */
  public Uploaded upload(File file, String productCode) throws IOException {
    if (!canUpload())
      throw new CloudinaryException(
          "Cloudinary no está configurado. Use «Configurar Cloudinary…» en la pestaña Imágenes.");
    byte[] jpeg = LocalImages.prepareJpeg(file);
    String folder = "productos/" + productCode.trim().replaceAll("[^A-Za-z0-9_-]", "_");
    Map<String, String> fields = new TreeMap<>();
    fields.put("folder", folder);
    if (!uploadPreset.isEmpty()) fields.put("upload_preset", uploadPreset);
    else sign(fields);
    String json = postMultipart(apiBase + "/" + enc(cloudName) + "/image/upload", fields, file.getName(), jpeg);
    String url = field(json, "secure_url"), id = field(json, "public_id");
    if (url == null || id == null) throw new CloudinaryException("Cloudinary no devolvió el enlace de la imagen.");
    return new Uploaded(url, id);
  }

  // ------------------------------------------------------------------ borrar

  /** Borra la imagen de Cloudinary. Si ya no existía, no es un error. */
  public void destroy(String publicId) throws IOException {
    if (!canDelete())
      throw new CloudinaryException("Para borrar en Cloudinary configure api_key y api_secret.");
    Map<String, String> fields = new TreeMap<>();
    fields.put("public_id", publicId);
    sign(fields);
    StringBuilder body = new StringBuilder();
    for (var e : fields.entrySet()) {
      if (body.length() > 0) body.append('&');
      body.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
    }
    String json =
        send(apiBase + "/" + enc(cloudName) + "/image/destroy", "application/x-www-form-urlencoded",
            body.toString().getBytes(StandardCharsets.UTF_8));
    String result = field(json, "result");
    if (!"ok".equals(result) && !"not found".equals(result))
      throw new CloudinaryException("Cloudinary no pudo borrar " + publicId + ".");
  }

  /** Firma de Cloudinary: parámetros ordenados "a=1&b=2" + api_secret, en SHA-1 hexadecimal. */
  private void sign(Map<String, String> fields) throws CloudinaryException {
    if (!canSign()) throw new CloudinaryException("Faltan api_key y api_secret de Cloudinary.");
    fields.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
    StringBuilder text = new StringBuilder();
    for (var e : new TreeMap<>(fields).entrySet()) {
      if (text.length() > 0) text.append('&');
      text.append(e.getKey()).append('=').append(e.getValue());
    }
    text.append(apiSecret);
    try {
      byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) hex.append(String.format("%02x", b));
      fields.put("signature", hex.toString());
      fields.put("api_key", apiKey);
    } catch (Exception e) {
      throw new CloudinaryException("No se pudo firmar la solicitud a Cloudinary.");
    }
  }

  // ------------------------------------------------------------------ URL

  /** ¿La URL es una imagen de esta cuenta de Cloudinary (y por tanto se puede borrar)? */
  public boolean isOwnUrl(String url) {
    return !cloudName.isEmpty() && url != null && url.startsWith(deliveryBase + "/" + cloudName + "/image/upload/");
  }

  /** ¿La URL es de Cloudinary (cualquier cuenta)? Sirve para pedir miniaturas. */
  public boolean isCloudinaryUrl(String url) {
    return url != null
        && (url.startsWith(deliveryBase + "/") || url.startsWith("https://res.cloudinary.com/"))
        && url.contains("/image/upload/");
  }

  /**
   * Obtiene el public_id desde la secure_url:
   * .../image/upload/[transformaciones/][v123/]productos/PER-002/foto.jpg → productos/PER-002/foto
   */
  public String publicIdFromUrl(String url) {
    if (!isCloudinaryUrl(url)) return null;
    String rest = url.substring(url.indexOf("/image/upload/") + "/image/upload/".length());
    int q = rest.indexOf('?');
    if (q >= 0) rest = rest.substring(0, q);
    List<String> parts = new ArrayList<>(Arrays.asList(rest.split("/")));
    while (parts.size() > 1 && (parts.get(0).contains(",") || parts.get(0).matches("[a-z]{1,3}_[^/]+"))) parts.remove(0);
    if (parts.size() > 1 && parts.get(0).matches("v\\d+")) parts.remove(0);
    String id = String.join("/", parts);
    int dot = id.lastIndexOf('.');
    if (dot > id.lastIndexOf('/')) id = id.substring(0, dot);
    try {
      return URLDecoder.decode(id, StandardCharsets.UTF_8);
    } catch (Exception e) {
      return id;
    }
  }

  /** URL con transformación de Cloudinary (por ejemplo w_300,c_limit,f_jpg). Otras URL, sin cambios. */
  public String transformed(String url, String transformation) {
    if (!isCloudinaryUrl(url)) return url;
    int i = url.indexOf("/image/upload/") + "/image/upload/".length();
    return url.substring(0, i) + transformation + "/" + url.substring(i);
  }

  public String thumbnailUrl(String url) {
    return transformed(url, "w_300,c_limit,f_jpg");
  }

  /** «Ver en grande»: tamaño original, pero en JPG para que Java lo pueda abrir (evita WebP). */
  public String largeUrl(String url) {
    return transformed(url, "f_jpg");
  }

  // ------------------------------------------------------------------ HTTP

  private String postMultipart(String url, Map<String, String> fields, String fileName, byte[] data) throws IOException {
    String boundary = "----datec" + UUID.randomUUID().toString().replace("-", "");
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    for (var e : fields.entrySet())
      body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + e.getKey() + "\"\r\n\r\n"
              + e.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
    String name = fileName.replaceAll("[\"\\r\\n]", "_").replaceFirst("\\.[^.]+$", "") + ".jpg";
    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name
            + "\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    body.write(data);
    body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return send(url, "multipart/form-data; boundary=" + boundary, body.toByteArray());
  }

  private String send(String url, String contentType, byte[] body) throws IOException {
    HttpURLConnection c;
    try {
      c = (HttpURLConnection) URI.create(url).toURL().openConnection();
      c.setConnectTimeout(10_000);
      c.setReadTimeout(60_000);
      c.setDoOutput(true);
      c.setRequestMethod("POST");
      c.setRequestProperty("Content-Type", contentType);
      try (OutputStream out = c.getOutputStream()) {
        out.write(body);
      }
    } catch (UnknownHostException | ConnectException | SocketTimeoutException e) {
      throw new CloudinaryException("No hay conexión con Cloudinary. Revise el internet del equipo.");
    }
    int status = c.getResponseCode();
    String json;
    try (InputStream in = status < 400 ? c.getInputStream() : c.getErrorStream()) {
      json = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    if (status >= 400) {
      String msg = field(json, "message");
      String text = msg == null ? "código " + status : msg;
      if (status == 401) text = "credenciales no válidas (" + text + ")";
      else if (text.toLowerCase(Locale.ROOT).contains("preset")) text = "el upload preset no existe o no es sin firma (" + text + ")";
      throw new CloudinaryException("Cloudinary rechazó la operación: " + text + ".");
    }
    return json;
  }

  private static String field(String json, String name) {
    Matcher m = Pattern.compile(String.format(FIELD.pattern(), Pattern.quote(name))).matcher(json);
    return m.find() ? m.group(1).replace("\\/", "/") : null;
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
