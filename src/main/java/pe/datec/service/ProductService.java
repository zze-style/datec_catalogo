package pe.datec.service;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import pe.datec.dao.ProductDao;
import pe.datec.model.Product;

/** Valida la ficha para informar al usuario antes de enviarla a las restricciones SQL. */
public final class ProductService {
  private final ProductDao dao;

  public ProductService(ProductDao dao) {
    this.dao = dao;
  }

  /** Resultado del guardado: avisos que no impidieron guardar. */
  public record SaveResult(List<String> warnings) {}

  /**
   * Guarda la ficha con sus imágenes, en este orden seguro:
   *
   * <ol>
   *   <li>sube a Cloudinary solo las fotos del equipo pendientes (carpeta productos/&lt;código&gt;/);
   *   <li>llama a sp_producto_guardar con la lista final de URL (una transacción en la base);
   *   <li>solo si la base guardó, borra en Cloudinary las imágenes propias que se quitaron.
   * </ol>
   *
   * Si falla una subida o la base, no se borra nada en Cloudinary; las fotos recién subidas de este
   * intento se retiran si hay credenciales para hacerlo.
   */
  public SaveResult saveWithImages(Product p, List<String> before, CloudinaryStorageService cloud)
      throws Exception {
    validateFields(p);
    for (String img : p.images())
      if (LocalImages.isPending(img) && !LocalImages.isImageFile(new File(img)))
        throw new IllegalArgumentException("La foto ya no está en el equipo: " + img);
    List<String> warnings = new ArrayList<>();
    List<CloudinaryStorageService.Uploaded> uploaded = new ArrayList<>();
    List<String> finalUrls = new ArrayList<>();
    try {
      for (String img : p.images()) {
        if (LocalImages.isPending(img)) {
          var up = cloud.upload(new File(img), p.code());
          uploaded.add(up);
          Thumbnails.alias(img, up.secureUrl());
          finalUrls.add(up.secureUrl());
        } else finalUrls.add(img);
      }
      Product toSave = p.withImages(finalUrls);
      validate(toSave);
      dao.save(toSave);
      // La base ya confirmó: recién ahora se borran en Cloudinary las imágenes quitadas.
      for (String old : before)
        if (!finalUrls.contains(old) && cloud.isOwnUrl(old)) {
          if (!cloud.canDelete()) {
            warnings.add("No se borró de Cloudinary (falta api_secret): " + old);
            continue;
          }
          try {
            cloud.destroy(cloud.publicIdFromUrl(old));
          } catch (Exception e) {
            warnings.add(e.getMessage());
          }
        }
      return new SaveResult(warnings);
    } catch (Exception e) {
      // No se guardó la ficha: se retiran las fotos subidas en este intento.
      List<String> orphans = new ArrayList<>();
      for (var up : uploaded) {
        try {
          if (cloud.canDelete()) cloud.destroy(up.publicId());
          else orphans.add(up.secureUrl());
        } catch (Exception ignored) {
          orphans.add(up.secureUrl());
        }
      }
      if (!orphans.isEmpty()) {
        String reason = String.valueOf(e.getMessage()).replaceFirst("^(ERROR|FATAL): ", "").split("\n")[0];
        throw new IllegalStateException(
            "No se guardó la ficha: " + reason + "\n\nEstas fotos quedaron en Cloudinary sin usar"
                + " (configure el api_secret para retirarlas automáticamente):\n" + String.join("\n", orphans), e);
      }
      throw e;
    }
  }

  /** Tras eliminar un producto en la base, borra sus imágenes propias de Cloudinary. */
  public static List<String> deleteCloudImages(List<String> images, CloudinaryStorageService cloud) {
    List<String> warnings = new ArrayList<>();
    for (String img : images)
      if (cloud.isOwnUrl(img)) {
        if (!cloud.canDelete()) {
          warnings.add("No se borró de Cloudinary (falta api_secret): " + img);
          continue;
        }
        try {
          cloud.destroy(cloud.publicIdFromUrl(img));
        } catch (Exception e) {
          warnings.add(e.getMessage());
        }
      }
    return warnings;
  }

  public static void validate(Product p) {
    validateFields(p);
    for (String image : p.images())
      if (!ImageStore.isUrl(image))
        throw new IllegalArgumentException(
            "Imagen: use un enlace que empiece con http:// o https:// (máximo 2048 caracteres).");
  }

  /** Valida todo menos las imágenes (que pueden estar pendientes de subir). */
  public static void validateFields(Product p) {
    required(p.code(), 50, "Código");
    required(p.name(), 150, "Nombre");
    if (p.model().length() > 80)
      throw new IllegalArgumentException("El modelo admite hasta 80 caracteres.");
    if (p.brand() == null || p.category() == null || p.presentation() == null)
      throw new IllegalArgumentException("Seleccione marca, categoría y presentación.");
    if (!java.util.Set.of("Nuevo", "Usado").contains(p.condition()))
      throw new IllegalArgumentException("Condición no válida.");
    price(p.retail(), true);
    price(p.wholesale(), false);
    if (p.wholesale() != null && p.minimum() != 2 && p.minimum() != 3)
      throw new IllegalArgumentException("La cantidad mayorista debe ser 2 o 3.");
    for (String feature : p.features()) required(feature, 255, "Característica");
  }

  private static void required(String text, int max, String field) {
    if (text == null || text.isBlank() || text.length() > max)
      throw new IllegalArgumentException(field + ": escriba entre 1 y " + max + " caracteres.");
  }

  private static void price(BigDecimal value, boolean required) {
    if (value == null) {
      if (required) throw new IllegalArgumentException("El precio minorista es obligatorio.");
      return;
    }
    if (value.signum() <= 0
        || value.compareTo(new BigDecimal("99999999.99")) > 0
        || value.stripTrailingZeros().scale() > 2)
      throw new IllegalArgumentException(
          "Los precios deben ser positivos, con hasta dos decimales y máximo 99999999.99.");
  }
}
