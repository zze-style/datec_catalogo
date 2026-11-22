package pe.datec.service;

import java.nio.file.Path;

/** Resuelve recursos también al ejecutar el paquete portable desde otra carpeta. */
public final class AppPaths {
  private AppPaths() {}

  public static Path home() {
    String override = System.getProperty("datec.home");
    if (override != null) return Path.of(override).toAbsolutePath();
    try {
      Path location =
          Path.of(AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      if (location.toString().endsWith(".jar")) return location.getParent();
    } catch (Exception ignored) {
    }
    return Path.of("").toAbsolutePath();
  }
}
