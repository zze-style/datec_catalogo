package pe.datec.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Entrada del índice de reportes. La lista completa (título, descripción, índice de la base y
 * filtros) la entrega sp_reportes_indice, de modo que la base de datos define los reportes.
 */
public record Report(
    int number,
    String group,
    String title,
    String description,
    String index,
    String column,
    List<String> filters) {

  @SuppressWarnings("unchecked")
  public static List<Report> from(DataTable t) {
    List<Report> result = new ArrayList<>();
    for (int r = 0; r < t.rows().size(); r++) {
      Object filters = t.get(r, "filtros");
      result.add(
          new Report(
              t.number(r, "numero"),
              t.text(r, "grupo"),
              t.text(r, "titulo"),
              t.text(r, "descripcion"),
              t.text(r, "indice"),
              t.text(r, "columna"),
              filters == null ? List.of() : List.copyOf((List<String>) filters)));
    }
    return result;
  }

  public boolean uses(String filter) {
    return filters.contains(filter);
  }

  @Override
  public String toString() {
    return number + ". " + title;
  }
}
