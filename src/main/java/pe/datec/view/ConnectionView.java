package pe.datec.view;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import pe.datec.dao.DbConfig;

/**
 * Inicio de sesión. Los datos del servidor se configuran una sola vez (o se usan los valores por
 * defecto del código fuente) y luego cada persona entra con su usuario y contraseña de DATEC, que
 * son usuarios de PostgreSQL con rol Administrador o Vendedor.
 */
public final class ConnectionView extends JPanel {
  public final JTextField user = Styles.input(28);
  public final JPasswordField password = new JPasswordField(28);
  public final JButton login = Styles.button("Ingresar", true),
      configure = Styles.button("Configurar servidor…", false);
  public final JLabel server = Styles.muted(" ");
  public final JLabel message = Styles.muted(" ");
  // Campos del diálogo "Configurar servidor"
  public final JTextField host = Styles.input(22), port = Styles.input(8), database = Styles.input(22);
  public final JComboBox<String> ssl =
      new JComboBox<>(new String[] {"prefer", "disable", "require", "verify-full"});
  private DbConfig current;

  public ConnectionView() {
    super(new GridBagLayout());
    setBackground(Styles.BG);
    JPanel card = new JPanel(new BorderLayout(0, 18));
    card.setBorder(
        new CompoundBorder(new LineBorder(Styles.LINE, 1, true), new EmptyBorder(34, 40, 30, 40)));
    JPanel top = new JPanel();
    top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
    JLabel logo = new JLabel(Logo.icon(72));
    JLabel title = Styles.heading("Iniciar sesión");
    JLabel subtitle = Styles.muted("DATÁLOGO · Catálogo de productos para Datec Informática");
    for (JComponent c : new JComponent[] {logo, title, subtitle}) c.setAlignmentX(0);
    top.add(logo);
    top.add(Box.createVerticalStrut(18));
    top.add(title);
    top.add(Box.createVerticalStrut(6));
    top.add(subtitle);
    card.add(top, BorderLayout.NORTH);
    password.setMargin(new Insets(7, 8, 7, 8));
    JPanel form = new JPanel(new GridLayout(0, 1, 0, 14));
    form.add(Styles.field("Usuario", user));
    form.add(Styles.field("Contraseña", password));
    form.add(login);
    card.add(form);
    JPanel bottom = new JPanel(new GridLayout(0, 1, 0, 8));
    bottom.add(message);
    JPanel serverRow = new JPanel(new BorderLayout(10, 0));
    serverRow.add(server);
    serverRow.add(configure, BorderLayout.EAST);
    bottom.add(serverRow);
    card.add(bottom, BorderLayout.SOUTH);
    form.setBorder(new EmptyBorder(4, 0, 4, 0));
    add(card);
    message.setForeground(new Color(180, 60, 40));
    load(DbConfig.load());
  }

  /** Muestra la configuración vigente. El usuario recordado no incluye contraseña. */
  public void load(DbConfig c) {
    current = c;
    if (user.getText().isBlank()) user.setText(c.user());
    host.setText(c.host());
    port.setText(String.valueOf(c.port()));
    database.setText(c.database());
    ssl.setSelectedItem(c.sslmode());
    server.setText("Servidor: " + c.host() + ":" + c.port() + " · base " + c.database());
  }

  /** Panel del diálogo de servidor (se configura una sola vez). */
  public JPanel serverForm() {
    load(current);
    JPanel form = new JPanel(new GridLayout(0, 2, 16, 14));
    form.add(Styles.field("Servidor", host));
    form.add(Styles.field("Puerto", port));
    form.add(Styles.field("Base de datos", database));
    form.add(Styles.field("Conexión SSL", ssl));
    return form;
  }

  /** Configuración del servidor leída del diálogo, sin credenciales. */
  public DbConfig serverValue() {
    int number;
    try {
      number = Integer.parseInt(port.getText().trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("El puerto debe ser un número.");
    }
    DbConfig c =
        new DbConfig(
            host.getText().trim(), number, database.getText().trim(), current.user(), "",
            (String) ssl.getSelectedItem());
    c.validate();
    return c;
  }

  /** Servidor configurado + usuario y contraseña escritos. */
  public DbConfig credentials() {
    char[] chars = password.getPassword();
    String secret = new String(chars);
    java.util.Arrays.fill(chars, '\0');
    String name = user.getText().trim();
    if (name.isEmpty() || secret.isEmpty())
      throw new IllegalArgumentException("Escriba su usuario y contraseña.");
    DbConfig c = current.withCredentials(name, secret);
    c.validate();
    return c;
  }
}
