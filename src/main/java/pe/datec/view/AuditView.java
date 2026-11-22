package pe.datec.view;

import java.awt.*;
import javax.swing.*;

public final class AuditView extends JPanel {
  public final JTextField search = Styles.input(28);
  public final JButton find = Styles.button("Consultar", true),
      export = Styles.button("Exportar CSV", false);
  public final JTable table = Styles.table();
  public final JTextArea detail = new JTextArea();
  public final JLabel count = Styles.muted("Últimos 1000 movimientos coincidentes.");

  public AuditView() {
    super(new BorderLayout());
    JPanel page = Styles.page();
    add(page);
    JPanel top = new JPanel(new GridLayout(0, 1, 0, 14));
    top.add(Styles.heading("Historial de cambios"));
    top.add(Styles.row(Styles.field("Tabla, operación, código o contenido", search), find, export));
    page.add(top, BorderLayout.NORTH);
    detail.setEditable(false);
    detail.setLineWrap(true);
    detail.setWrapStyleWord(true);
    detail.setRows(7);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    JSplitPane split =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, Styles.scroll(table), Styles.scroll(detail));
    split.setResizeWeight(.7);
    split.setBorder(null);
    page.add(split);
    page.add(count, BorderLayout.SOUTH);
    export.setEnabled(false);
  }
}
