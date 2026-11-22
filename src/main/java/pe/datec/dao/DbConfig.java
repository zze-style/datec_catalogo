package pe.datec.dao;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import pe.datec.service.AppPaths;

/**
 * Datos de conexión. Los valores por defecto están en el código fuente (DEFAULT_*), así que en un
 * equipo con PostgreSQL local no hace falta configurar nada. Si el servidor es otro, se cambia una
 * sola vez en "Configurar servidor" y queda guardado en config/local.properties (sin contraseña).
 */
public record DbConfig(
    String host, int port, String database, String user, String password, String sslmode) {
  public static final String DEFAULT_HOST = "localhost";
  public static final int DEFAULT_PORT = 5432;
  public static final String DEFAULT_DATABASE = "datec_catalogo";
  public static final String DEFAULT_SSL = "prefer";

  public static DbConfig load() {
    Properties p = new Properties();
    for (String name : new String[] {"database.properties", "local.properties"}) {
      Path file = AppPaths.home().resolve("config").resolve(name);
      if (Files.exists(file))
        try (InputStream in = Files.newInputStream(file)) {
          p.load(in);
        } catch (IOException ignored) {
        }
    }
    int port;
    try {
      port = Integer.parseInt(p.getProperty("port", String.valueOf(DEFAULT_PORT)));
    } catch (NumberFormatException e) {
      port = DEFAULT_PORT;
    }
    return new DbConfig(
        p.getProperty("host", DEFAULT_HOST),
        port,
        p.getProperty("database", DEFAULT_DATABASE),
        p.getProperty("user", ""),
        "",
        p.getProperty("sslmode", DEFAULT_SSL));
  }

  public DbConfig withCredentials(String newUser, String newPassword) {
    return new DbConfig(host, port, database, newUser, newPassword, sslmode);
  }

  /** Guarda servidor y último usuario. La contraseña nunca se escribe en disco. */
  public void save() throws IOException {
    // Se conservan las demás claves del archivo (por ejemplo, ajustes locales).
    Properties p = new Properties();
    Path file = AppPaths.home().resolve("config/local.properties");
    if (Files.exists(file))
      try (InputStream in = Files.newInputStream(file)) {
        p.load(in);
      }
    p.setProperty("host", host);
    p.setProperty("port", String.valueOf(port));
    p.setProperty("database", database);
    p.setProperty("user", user == null ? "" : user);
    p.setProperty("sslmode", sslmode);
    Files.createDirectories(file.getParent());
    try (OutputStream out = Files.newOutputStream(file)) {
      p.store(out, "Conexion DATEC. No contiene contrasena.");
    }
  }

  public void validate() {
    if (host.isBlank() || !host.matches("[A-Za-z0-9._:-]+"))
      throw new IllegalArgumentException("Servidor no válido.");
    if (port < 1 || port > 65535)
      throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535.");
    if (!database.matches("[A-Za-z0-9_-]+"))
      throw new IllegalArgumentException("Nombre de base de datos no válido.");
    if (!java.util.Set.of("disable", "prefer", "require", "verify-full").contains(sslmode))
      throw new IllegalArgumentException("Modo SSL no válido.");
  }
}
