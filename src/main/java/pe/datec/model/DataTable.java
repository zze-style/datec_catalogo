package pe.datec.model;

import java.util.List;

/** Resultado desconectado: los cursores y conexiones ya están cerrados al llegar a la vista. */
public record DataTable(List<String> columns, List<List<Object>> rows) {
  public Object get(int row, String column) {
    int index = columns.indexOf(column);
    if (index < 0) throw new IllegalArgumentException("Columna desconocida: " + column);
    return rows.get(row).get(index);
  }

  public String text(int row, String column) {
    Object value = get(row, column);
    return value == null ? "" : value.toString();
  }

  public int number(int row, String column) {
    return ((Number) get(row, column)).intValue();
  }

  public static DataTable empty() {
    return new DataTable(List.of(), List.of());
  }
}
