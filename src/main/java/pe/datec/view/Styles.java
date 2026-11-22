package pe.datec.view;

import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import pe.datec.model.*;

/** Estilo construido con Swing estándar: sin temas externos ni frameworks. */
public final class Styles {
  public static final Color BG = new Color(248, 250, 252),
      INK = new Color(31, 44, 64),
      MUTED = new Color(85, 101, 122),
      LINE = new Color(218, 226, 236),
      BLUE = new Color(36, 92, 170),
      SELECTED = new Color(231, 240, 253);

  private Styles() {}

  public static void install() {
    try {
      UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
    } catch (Exception ignored) {
    }
    Font font = new Font("Segoe UI", Font.PLAIN, 14);
    for (Object key : java.util.Collections.list(UIManager.getDefaults().keys()))
      if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)
        UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
    UIManager.put("Panel.background", Color.WHITE);
    UIManager.put("Label.foreground", INK);
    UIManager.put("TextField.background", Color.WHITE);
    UIManager.put("TextArea.background", Color.WHITE);
    UIManager.put("ComboBox.background", Color.WHITE);
    UIManager.put("Table.selectionBackground", SELECTED);
    UIManager.put("Table.selectionForeground", INK);
    UIManager.put("TabbedPane.selected", Color.WHITE);
  }

  public static JButton button(String text, boolean primary) {
    JButton b = new JButton(text);
    b.setBackground(primary ? BLUE : Color.WHITE);
    b.setForeground(primary ? Color.WHITE : INK);
    b.setFocusPainted(true);
    b.setOpaque(true);
    b.setBorder(
        new CompoundBorder(new LineBorder(primary ? BLUE : LINE), new EmptyBorder(9, 15, 9, 15)));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  public static JLabel heading(String text) {
    JLabel l = new JLabel(text);
    l.setFont(new Font("Segoe UI", Font.BOLD, 26));
    return l;
  }

  public static JLabel muted(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(MUTED);
    return l;
  }

  public static JPanel row(Component... components) {
    JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
    p.setOpaque(false);
    for (Component c : components) p.add(c);
    return p;
  }

  public static JPanel field(String label, JComponent input) {
    JPanel p = new JPanel(new BorderLayout(0, 7));
    p.add(muted(label), BorderLayout.NORTH);
    JPanel body = new JPanel(new BorderLayout());
    body.add(input, BorderLayout.NORTH);
    p.add(body);
    return p;
  }

  public static JPanel page() {
    JPanel p = new JPanel(new BorderLayout(16, 18));
    p.setBorder(new EmptyBorder(24, 26, 20, 26));
    return p;
  }

  public static JTextField input(int cols) {
    JTextField f = new JTextField(cols);
    f.setMargin(new Insets(7, 8, 7, 8));
    return f;
  }

  public static JTable table() {
    JTable t = new JTable();
    t.setRowHeight(44);
    t.setShowVerticalLines(false);
    t.setGridColor(LINE);
    t.setIntercellSpacing(new Dimension(0, 1));
    t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    t.setAutoCreateRowSorter(true);
    t.setFillsViewportHeight(true);
    t.getTableHeader().setReorderingAllowed(false);
    t.getTableHeader().setPreferredSize(new Dimension(0, 38));
    DefaultTableCellRenderer header = new DefaultTableCellRenderer();
    header.setBackground(BG);
    header.setForeground(MUTED);
    header.setBorder(new EmptyBorder(9, 10, 9, 10));
    t.getTableHeader().setDefaultRenderer(header);
    DefaultTableCellRenderer cells =
        new DefaultTableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(
              JTable table, Object value, boolean selected, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, col);
            setBorder(new EmptyBorder(7, 10, 7, 10));
            setForeground(INK);
            setBackground(selected ? SELECTED : Color.WHITE);
            setHorizontalAlignment(value instanceof Number ? RIGHT : LEFT);
            setText(
                value == null
                    ? "—"
                    : value instanceof BigDecimal decimal
                        ? decimal.setScale(2).toPlainString()
                        : String.valueOf(value));
            setToolTipText(value == null ? null : String.valueOf(value));
            return this;
          }
        };
    t.setDefaultRenderer(Object.class, cells);
    t.setDefaultRenderer(Number.class, cells);
    return t;
  }

  public static void setData(JTable table, DataTable data) {
    table.setModel(
        new AbstractTableModel() {
          public int getRowCount() {
            return data.rows().size();
          }

          public int getColumnCount() {
            return data.columns().size();
          }

          public String getColumnName(int c) {
            return data.columns().get(c);
          }

          public Object getValueAt(int r, int c) {
            return data.rows().get(r).get(c);
          }

          public Class<?> getColumnClass(int c) {
            for (List<Object> row : data.rows())
              if (row.get(c) instanceof Number) return row.get(c).getClass();
            return Object.class;
          }
        });
    for (int c = 0; c < table.getColumnCount(); c++)
      table.getColumnModel().getColumn(c).setPreferredWidth(c == 1 ? 280 : 150);
  }

  public static void lookups(JComboBox<Lookup> combo, List<Lookup> items, String all) {
    Integer previous = selected(combo);
    combo.removeAllItems();
    if (all != null) combo.addItem(new Lookup(null, all));
    for (Lookup item : items) combo.addItem(item);
    select(combo, previous);
  }

  public static Integer selected(JComboBox<Lookup> combo) {
    Lookup l = (Lookup) combo.getSelectedItem();
    return l == null ? null : l.id();
  }

  public static void select(JComboBox<Lookup> combo, Integer id) {
    for (int i = 0; i < combo.getItemCount(); i++)
      if (java.util.Objects.equals(combo.getItemAt(i).id(), id)) {
        combo.setSelectedIndex(i);
        return;
      }
  }

  public static JScrollPane scroll(JComponent c) {
    JScrollPane s = new JScrollPane(c);
    s.setBorder(new LineBorder(LINE));
    if (c instanceof JTable t) s.setColumnHeaderView(t.getTableHeader());
    return s;
  }
}
