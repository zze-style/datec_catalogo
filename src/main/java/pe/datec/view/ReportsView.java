package pe.datec.view;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import pe.datec.model.*;

/**
 * Reportes organizados como un índice: a la izquierda la lista que entrega la base de datos; a la
 * derecha la descripción, el índice de PostgreSQL en que se apoya, solo los filtros que ese
 * reporte usa, el resultado y su plan de ejecución.
 */
public final class ReportsView extends JPanel {
  public final DefaultListModel<Report> model = new DefaultListModel<>();
  public final JList<Report> list = new JList<>(model);
  public final JTextField text = Styles.input(20), minimum = Styles.input(8), maximum = Styles.input(8);
  public final JComboBox<Lookup> product = new JComboBox<>(),
      category = new JComboBox<>(),
      brand = new JComboBox<>(),
      presentation = new JComboBox<>();
  public final JComboBox<String> condition =
      new JComboBox<>(new String[] {"Todas", "Nuevo", "Usado"});
  public final JButton run = Styles.button("Consultar", true),
      export = Styles.button("Exportar CSV", false),
      plan = Styles.button("Ver plan de ejecución", false);
  public final JCheckBox simulate = new JCheckBox("Simular catálogo grande");
  public final JTable table = Styles.table();
  public final JLabel count = Styles.muted("Elija un reporte del índice.");
  private final JLabel title = Styles.heading("Reportes"),
      description = Styles.muted(" "),
      index = new JLabel(" ");
  private final JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
  private final Map<String, JComponent> fields = new LinkedHashMap<>();

  public ReportsView() {
    super(new BorderLayout());
    JPanel page = Styles.page();
    add(page);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new IndexRenderer());
    JPanel left = new JPanel(new BorderLayout(0, 10));
    left.setPreferredSize(new Dimension(290, 0));
    JLabel leftTitle = new JLabel("Índice de reportes");
    leftTitle.setFont(leftTitle.getFont().deriveFont(Font.BOLD, 16f));
    left.add(leftTitle, BorderLayout.NORTH);
    JScrollPane listScroll = Styles.scroll(list);
    listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    left.add(listScroll);

    product.setPreferredSize(new Dimension(300, 36));
    category.setPreferredSize(new Dimension(210, 36));
    brand.setPreferredSize(new Dimension(190, 36));
    presentation.setPreferredSize(new Dimension(200, 36));
    condition.setPreferredSize(new Dimension(120, 36));
    fields.put("texto", Styles.field("Comienza con", text));
    fields.put("categoria", Styles.field("Categoría", category));
    fields.put("marca", Styles.field("Marca", brand));
    fields.put("presentacion", Styles.field("Presentación", presentation));
    fields.put("producto", Styles.field("Producto", product));
    fields.put("condicion", Styles.field("Condición", condition));
    JPanel range = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    range.add(Styles.field("Precio desde S/", minimum));
    range.add(Styles.field("hasta S/", maximum));
    fields.put("precio", range);
    filters.setOpaque(false);

    index.setForeground(Styles.BLUE);
    index.setBorder(
        new CompoundBorder(new LineBorder(Styles.SELECTED.darker(), 1, true), new EmptyBorder(6, 10, 6, 10)));
    index.setOpaque(true);
    index.setBackground(Styles.SELECTED);
    simulate.setOpaque(false);
    simulate.setToolTipText(
        "Con pocos registros PostgreSQL lee la tabla completa. Esta opción desactiva ese recorrido"
            + " solo para el plan, y muestra el índice que usaría con un catálogo grande.");

    JPanel head = new JPanel(new GridLayout(0, 1, 0, 8));
    head.add(title);
    head.add(description);
    JPanel indexRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    indexRow.add(index);
    head.add(indexRow);
    JPanel controls = new JPanel(new BorderLayout(0, 10));
    controls.add(head, BorderLayout.NORTH);
    controls.add(filters);
    controls.add(Styles.row(run, export, plan, simulate), BorderLayout.SOUTH);

    JPanel right = new JPanel(new BorderLayout(0, 14));
    right.add(controls, BorderLayout.NORTH);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    right.add(Styles.scroll(table));
    right.add(count, BorderLayout.SOUTH);

    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
    split.setBorder(null);
    split.setDividerSize(6);
    split.setDividerLocation(290);
    page.add(split);
    export.setEnabled(false);
    list.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) showReport(list.getSelectedValue());
        });
    showReport(null);
  }

  public void setReports(List<Report> reports) {
    Report previous = list.getSelectedValue();
    model.clear();
    reports.forEach(model::addElement);
    int select = 0;
    for (int i = 0; previous != null && i < model.size(); i++)
      if (model.get(i).number() == previous.number()) select = i;
    if (!model.isEmpty()) list.setSelectedIndex(select);
  }

  public Report selected() {
    return list.getSelectedValue();
  }

  /** Muestra la ficha del reporte y únicamente los filtros que usa. */
  public void showReport(Report r) {
    filters.removeAll();
    run.setEnabled(r != null);
    plan.setEnabled(r != null);
    simulate.setEnabled(r != null);
    if (r == null) {
      title.setText("Reportes del catálogo");
      description.setText("Seleccione un reporte en el índice de la izquierda.");
      index.setText("Índice: —");
    } else {
      title.setText(r.number() + ". " + r.title());
      description.setText(r.description());
      index.setText("Índice de la base: " + r.index() + "  ·  " + r.column());
      for (String f : r.filters()) if (fields.containsKey(f)) filters.add(fields.get(f));
      if (r.filters().isEmpty()) filters.add(Styles.muted("Este reporte no necesita filtros."));
    }
    filters.revalidate();
    filters.repaint();
  }

  /** Lista con encabezado de grupo y nombre del índice debajo del título. */
  private static final class IndexRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(
        JList<?> l, Object value, int i, boolean selected, boolean focus) {
      Report r = (Report) value;
      Report before = i > 0 ? (Report) l.getModel().getElementAt(i - 1) : null;
      boolean header = before == null || !before.group().equals(r.group());
      String html =
          "<html>"
              + (header
                  ? "<div style='color:#55657a;font-size:10px;margin-top:6px'>"
                      + r.group().toUpperCase()
                      + "</div>"
                  : "")
              + "<div style='margin-top:3px;width:215px'><b>"
              + r.number()
              + ".</b> "
              + r.title()
              + "</div><div style='color:#245caa;font-size:10px'>"
              + r.index()
              + "</div></html>";
      super.getListCellRendererComponent(l, html, i, selected, focus);
      setBorder(new EmptyBorder(4, 10, 6, 10));
      setBackground(selected ? Styles.SELECTED : Color.WHITE);
      setForeground(Styles.INK);
      return this;
    }
  }
}
