package pe.datec.view;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import pe.datec.model.*;

/** Vista pasiva del catálogo. No importa JDBC ni ejecuta SQL. */
public final class CatalogView extends JPanel {
  public final JTextField search = Styles.input(23);
  public final JComboBox<Lookup> category = new JComboBox<>(), brand = new JComboBox<>();
  public final JComboBox<String>
      condition = new JComboBox<>(new String[] {"Todas", "Nuevo", "Usado"});
  public final JButton find = Styles.button("Buscar", false),
      create = Styles.button("Nuevo producto", true),
      edit = Styles.button("Editar ficha", true),
      remove = Styles.button("Eliminar producto", false);
  public final JTable table = Styles.table();
  public final JLabel count = Styles.muted("Conecte la base de datos para consultar el catálogo.");
  public final JTextArea detail = new JTextArea();
  public final ImageViewer images = new ImageViewer(250, 170);
  private DataTable data = DataTable.empty();

  public CatalogView() {
    super(new BorderLayout());
    JPanel page = Styles.page();
    add(page);
    category.setPreferredSize(new Dimension(220, 36));
    brand.setPreferredSize(new Dimension(190, 36));
    condition.setPreferredSize(new Dimension(110, 36));
    JPanel top = new JPanel(new BorderLayout(0, 18));
    JPanel title = new JPanel(new BorderLayout());
    title.add(Styles.heading("Productos"));
    title.add(create, BorderLayout.EAST);
    top.add(title, BorderLayout.NORTH);
    JPanel filters = new JPanel(new GridLayout(2, 1, 0, 9));
    filters.add(
        Styles.row(
            Styles.field("Nombre, modelo o código", search),
            Styles.field("Categoría", category),
            find));
    filters.add(
        Styles.row(
            Styles.field("Marca", brand),
            Styles.field("Condición", condition)));
    top.add(filters);
    page.add(top, BorderLayout.NORTH);
    JPanel right = new JPanel(new BorderLayout(8, 12));
    right.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 8));
    right.setPreferredSize(new Dimension(290, 400));
    detail.setEditable(false);
    detail.setLineWrap(true);
    detail.setWrapStyleWord(true);
    detail.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    detail.setForeground(Styles.INK);
    detail.setText("Seleccione un producto para consultar su ficha.");
    right.add(images, BorderLayout.NORTH);
    right.add(Styles.scroll(detail));
    JPanel actions = new JPanel(new GridLayout(0, 1, 0, 8));
    actions.add(edit);
    actions.add(remove);
    right.add(actions, BorderLayout.SOUTH);
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, Styles.scroll(table), right);
    split.setResizeWeight(.73);
    split.setBorder(null);
    split.setDividerSize(5);
    page.add(split);
    page.add(count, BorderLayout.SOUTH);
    setSelected(null);
  }

  public void display(DataTable result) {
    data = result;
    java.util.List<java.util.List<Object>> rows = new ArrayList<>();
    for (int r = 0; r < result.rows().size(); r++)
      rows.add(
          Arrays.asList(
              result.get(r, "codigoproducto"),
              result.get(r, "nombreproducto"),
              result.get(r, "nombremarca"),
              result.get(r, "minorista"),
              result.get(r, "estadoproducto"),
              result.get(r, "imagenes")));
    Styles.setData(
        table,
        new DataTable(
            java.util.List.of("Código", "Producto", "Marca", "Minorista S/", "Condición", "Imágenes"), rows));
    count.setText(
        result.rows().size()
            + " productos · Los precios corresponden a la presentación comercial.");
    setSelected(null);
  }

  public Integer selectedId() {
    int r = table.getSelectedRow();
    return r < 0 ? null : data.number(table.convertRowIndexToModel(r), "idproducto");
  }

  public void setSelected(Product p) {
    edit.setEnabled(p != null);
    remove.setEnabled(p != null);
    images.setImages(p == null ? java.util.List.of() : p.images());
    if (p == null) {
      detail.setText("Seleccione un producto para consultar su ficha.");
      return;
    }
    detail.setText(
        p.name()
            + "\n"
            + p.code()
            + " · "
            + p.condition()
            + "\n\nModelo: "
            + p.model()
            + "\n\n"
            + p.description()
            + "\n\nMinorista: "
            + (p.retail() == null ? "Sin tarifa" : "S/ " + p.retail())
            + "\nMayorista: "
            + (p.wholesale() == null
                ? "Sin tarifa"
                : "S/ " + p.wholesale() + " desde " + p.minimum() + " presentaciones")
            + "\n\nCaracterísticas\n"
            + String.join("\n", p.features()));
    detail.setCaretPosition(0);
  }

  /** El vendedor solo consulta: se ocultan las acciones de mantenimiento. */
  public void setAdmin(boolean admin) {
    create.setVisible(admin);
    edit.setVisible(admin);
    remove.setVisible(admin);
  }
}
