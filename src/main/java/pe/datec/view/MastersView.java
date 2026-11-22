package pe.datec.view;

import java.awt.*;
import javax.swing.*;

public final class MastersView extends JPanel {
  public final JComboBox<String> type =
      new JComboBox<>(new String[] {"Marca", "Categoria", "TipoPresentacion"});
  public static String label(String type) {
    return switch (type) {
      case "Categoria" -> "Categoría";
      case "TipoPresentacion" -> "Presentación";
      default -> type;
    };
  }

  public final JButton refresh = Styles.button("Actualizar", false),
      create = Styles.button("Agregar", true),
      edit = Styles.button("Modificar", false),
      remove = Styles.button("Eliminar", false);
  public final JTable table = Styles.table();
  public final JLabel count = Styles.muted("Seleccione una clasificación.");

  public MastersView() {
    super(new BorderLayout());
    JPanel page = Styles.page();
    add(page);
    JPanel top = new JPanel(new GridLayout(0, 1, 0, 14));
    top.add(Styles.heading("Clasificaciones"));
    top.add(Styles.row(type, refresh, create, edit, remove));
    page.add(top, BorderLayout.NORTH);
    type.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> l, Object v, int i, boolean s, boolean f) {
            return super.getListCellRendererComponent(l, v == null ? "" : label((String) v), i, s, f);
          }
        });
    page.add(Styles.scroll(table));
    page.add(count, BorderLayout.SOUTH);
  }
}
