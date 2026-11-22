package pe.datec.dao;

import java.sql.SQLException;
import pe.datec.model.*;

public final class ProductDao {
  private final RoutineDao db;

  public ProductDao(RoutineDao db) {
    this.db = db;
  }

  public DataTable search(
      String text, Integer category, Integer brand, String condition)
      throws SQLException {
    return db.query("sp_catalogo", text, category, brand, condition);
  }

  public Product get(int id) throws SQLException {
    return Product.from(db.query("sp_producto_detalle", id));
  }

  public int save(Product p) throws SQLException {
    return db.execute(
        "sp_producto_guardar",
        p.id(),
        p.code(),
        p.name(),
        p.model(),
        p.description(),
        p.brand(),
        p.category(),
        p.presentation(),
        p.condition(),
        p.retail(),
        p.wholesale(),
        p.minimum(),
        p.features().toArray(String[]::new),
        p.images().toArray(String[]::new));
  }

  /** Elimina el producto con sus precios, imágenes y características (una transacción). */
  public void delete(int id) throws SQLException {
    db.execute("sp_producto_eliminar", id);
  }
}
