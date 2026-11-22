package pe.datec.view;

import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.*;
import pe.datec.model.*;

/** Formulario de ficha completa. Las listas se editan una entrada por línea. */
public final class ProductEditor extends JPanel {
  public final JButton save = Styles.button("Guardar cambios", true),
      cancel = Styles.button("Volver al catálogo", false);
  public final JTextField code = Styles.input(20),
      name = Styles.input(30),
      model = Styles.input(20),
      retail = Styles.input(12),
      wholesale = Styles.input(12);
  public final JTextArea description = new JTextArea(5, 30),
      features = new JTextArea(14, 40);
  public final ImageGallery gallery = new ImageGallery();
  public final JComboBox<Lookup> brand = new JComboBox<>(),
      category = new JComboBox<>(),
      presentation = new JComboBox<>();
  public final JComboBox<String> condition = new JComboBox<>(new String[] {"Nuevo", "Usado"});
  public final JComboBox<Integer> minimum = new JComboBox<>(new Integer[] {2, 3});
  private Product original;
  private final JLabel title = Styles.heading("Nuevo producto");

  public ProductEditor() {
    super(new BorderLayout());
    JPanel page = Styles.page();
    add(page);
    JPanel heading = new JPanel(new BorderLayout());
    heading.add(title);
    heading.add(cancel, BorderLayout.EAST);
    page.add(heading, BorderLayout.NORTH);
    JTabbedPane tabs = new JTabbedPane();
    JPanel info = new JPanel(new GridBagLayout());
    info.setBorder(BorderFactory.createEmptyBorder(22, 20, 22, 20));
    JComponent[] inputs = {code, name, brand, category, presentation, condition, model};
    String[] labels = {
      "Código *", "Nombre *", "Marca *", "Categoría *", "Presentación *", "Condición *", "Modelo"
    };
    GridBagConstraints cell = new GridBagConstraints();
    cell.weightx = 1;
    cell.fill = GridBagConstraints.HORIZONTAL;
    cell.anchor = GridBagConstraints.NORTHWEST;
    for (int i = 0; i < inputs.length; i++) {
      cell.gridx = i % 2;
      cell.gridy = i / 2;
      cell.insets = new Insets(0, 0, 18, i % 2 == 0 ? 20 : 0);
      info.add(Styles.field(labels[i], inputs[i]), cell);
    }
    description.setLineWrap(true);
    description.setWrapStyleWord(true);
    cell.gridx = 0;
    cell.gridy = 4;
    cell.gridwidth = 2;
    cell.insets = new Insets(0, 0, 0, 0);
    info.add(Styles.field("Descripción", Styles.scroll(description)), cell);
    JPanel infoContainer = new JPanel(new BorderLayout());
    infoContainer.add(info, BorderLayout.NORTH);
    tabs.addTab("Información", Styles.scroll(infoContainer));
    JPanel prices = new JPanel(new GridLayout(0, 1, 10, 18));
    prices.setBorder(BorderFactory.createEmptyBorder(24, 24, 100, 180));
    prices.add(Styles.field("Precio minorista en soles *", retail));
    prices.add(Styles.field("Precio mayorista en soles (opcional)", wholesale));
    prices.add(Styles.field("Cantidad mínima de presentaciones para mayorista", minimum));
    prices.add(Styles.muted("El encargado determina el mínimo según el costo de adquisición."));
    tabs.addTab("Precios", Styles.scroll(prices));
    JPanel featurePanel = Styles.page();
    featurePanel.add(
        Styles.muted("Una característica por línea. Máximo 255 caracteres por característica."),
        BorderLayout.NORTH);
    featurePanel.add(Styles.scroll(features));
    tabs.addTab("Características", featurePanel);
    tabs.addTab("Imágenes", gallery);
    page.add(tabs);
    page.add(
        Styles.row(
            Styles.muted("* Campos obligatorios. Todos los cambios se guardan juntos."), save),
        BorderLayout.SOUTH);
  }

  public void open(Product p) {
    original = p;
    title.setText(p == null ? "Nuevo producto" : "Editar producto · " + p.code());
    code.setText(p == null ? "" : p.code());
    name.setText(p == null ? "" : p.name());
    model.setText(p == null ? "" : p.model());
    description.setText(p == null ? "" : p.description());
    retail.setText(p == null || p.retail() == null ? "" : p.retail().toPlainString());
    wholesale.setText(p == null || p.wholesale() == null ? "" : p.wholesale().toPlainString());
    condition.setSelectedItem(p == null ? "Nuevo" : p.condition());
    minimum.setSelectedItem(p == null ? 3 : p.minimum());
    features.setText(p == null ? "" : String.join("\n", p.features()));
    gallery.setPaths(p == null ? List.of() : p.images());
    if (p != null) {
      Styles.select(brand, p.brand());
      Styles.select(category, p.category());
      Styles.select(presentation, p.presentation());
    }
  }

  public Product value() {
    return new Product(
        original == null ? null : original.id(),
        code.getText().trim(),
        name.getText().trim(),
        model.getText().trim(),
        description.getText().trim(),
        Styles.selected(brand),
        Styles.selected(category),
        Styles.selected(presentation),
        (String) condition.getSelectedItem(),
        decimal(retail.getText()),
        decimal(wholesale.getText()),
        (Integer) minimum.getSelectedItem(),
        lines(features),
        gallery.paths());
  }

  private static BigDecimal decimal(String text) {
    if (text.isBlank()) return null;
    try {
      return new BigDecimal(text.trim().replace(',', '.'));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Escriba precios como 90.50, sin separadores de miles.");
    }
  }

  private static List<String> lines(JTextArea area) {
    return area.getText().lines().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
  }
}
