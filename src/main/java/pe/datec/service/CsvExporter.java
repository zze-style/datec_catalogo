package pe.datec.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.JTable;

/** Exporta el orden visible y neutraliza fórmulas al abrir el CSV en una hoja de cálculo. */
public final class CsvExporter {
  private CsvExporter() {}

  public static String cell(Object value) {
    String s = value == null ? "" : value.toString();
    String leading = s.stripLeading();
    if (value instanceof String && !leading.isEmpty() && "=+@-".indexOf(leading.charAt(0)) >= 0)
      s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  public static void export(JTable table, Path file) throws IOException {
    try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      w.write('\ufeff');
      for (int r = -1; r < table.getRowCount(); r++) {
        for (int c = 0; c < table.getColumnCount(); c++) {
          if (c > 0) w.write(';');
          w.write(cell(r < 0 ? table.getColumnName(c) : table.getValueAt(r, c)));
        }
        w.newLine();
      }
    }
  }
}
