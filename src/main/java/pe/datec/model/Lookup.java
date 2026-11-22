package pe.datec.model;

/** Elemento de una lista desplegable; conserva el identificador de la base. */
public record Lookup(Integer id, String name) {
  @Override
  public String toString() {
    return name;
  }
}
