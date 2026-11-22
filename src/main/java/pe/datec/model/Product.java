package pe.datec.model;

import java.math.BigDecimal;
import java.util.List;

/** Ficha de catálogo: corresponde a Producto y sus tablas dependientes. */
public record Product(
    Integer id,
    String code,
    String name,
    String model,
    String description,
    Integer brand,
    Integer category,
    Integer presentation,
    String condition,
    BigDecimal retail,
    BigDecimal wholesale,
    int minimum,
    List<String> features,
    List<String> images) {
  public Product withImages(List<String> newImages) {
    return new Product(id, code, name, model, description, brand, category, presentation, condition,
        retail, wholesale, minimum, features, List.copyOf(newImages));
  }

  @SuppressWarnings("unchecked")
  public static Product from(DataTable t) {
    if (t.rows().isEmpty()) throw new IllegalArgumentException("El producto ya no existe.");
    Object minimum = t.get(0, "cantidadminima");
    return new Product(
        t.number(0, "idproducto"),
        t.text(0, "codigoproducto"),
        t.text(0, "nombreproducto"),
        t.text(0, "modelo"),
        t.text(0, "descripcion"),
        t.number(0, "idmarca"),
        t.number(0, "idcategoria"),
        t.number(0, "idtipopresentacion"),
        t.text(0, "estadoproducto"),
        (BigDecimal) t.get(0, "minorista"),
        (BigDecimal) t.get(0, "mayorista"),
        minimum == null ? 3 : ((Number) minimum).intValue(),
        (List<String>) t.get(0, "caracteristicas"),
        (List<String>) t.get(0, "imagenes"));
  }
}
