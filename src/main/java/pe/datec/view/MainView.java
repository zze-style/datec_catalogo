package pe.datec.view;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import pe.datec.model.Session;

/** Ventana principal: menú lateral según el rol, contenido y barra de estado. */
public final class MainView extends JPanel {
  /** Pantallas que solo ve el Administrador. */
  public static final Set<String> ADMIN_ONLY = Set.of("masters", "audit", "users");

  public final CatalogView catalog = new CatalogView();
  public final ProductEditor editor = new ProductEditor();
  public final ReportsView reports = new ReportsView();
  public final MastersView masters = new MastersView();
  public final AuditView audit = new AuditView();
  public final UsersView users = new UsersView();
  public final ConnectionView connection = new ConnectionView();
  public final Map<String, JButton> nav = new LinkedHashMap<>();
  public final JButton logout = Styles.button("Cerrar sesión", false);
  public final JLabel status = Styles.muted("Sin sesión");
  private final JLabel userName = new JLabel(" "), userRole = Styles.muted(" ");
  private final JPanel menu = new JPanel(), account = new JPanel();
  private final JPanel sidebar = new JPanel(new BorderLayout());
  private final CardLayout layout = new CardLayout();
  private final JPanel content = new JPanel(layout);
  public String current = "connection";
  public boolean dirtyEditor = false;

  public MainView() {
    super(new BorderLayout());
    setPreferredSize(new Dimension(1250, 800));
    sidebar.setBackground(Styles.BG);
    sidebar.setPreferredSize(new Dimension(185, 0));
    sidebar.setBorder(new EmptyBorder(30, 16, 20, 16));
    JPanel labels = new JPanel(new GridLayout(0, 1, 0, 3));
    labels.setOpaque(false);
    JLabel brand = Styles.heading("DATÁLOGO");
    brand.setIcon(Logo.icon(30));
    brand.setIconTextGap(8);
    brand.setFont(brand.getFont().deriveFont(21f));
    labels.add(brand);
    sidebar.add(labels, BorderLayout.NORTH);
    menu.setOpaque(false);
    menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
    menu.setBorder(new EmptyBorder(32, 0, 0, 0));
    for (String[] item :
        new String[][] {
          {"catalog", "Productos"},
          {"masters", "Clasificaciones"},
          {"reports", "Reportes"},
          {"audit", "Historial"},
          {"users", "Usuarios"}
        }) {
      JButton button = Styles.button(item[1], false);
      button.setMaximumSize(new Dimension(155, 42));
      button.setAlignmentX(0);
      nav.put(item[0], button);
    }
    sidebar.add(menu);
    account.setOpaque(false);
    account.setLayout(new BoxLayout(account, BoxLayout.Y_AXIS));
    logout.setMaximumSize(new Dimension(155, 40));
    for (JComponent c : new JComponent[] {userName, userRole, logout}) c.setAlignmentX(0);
    userName.setFont(userName.getFont().deriveFont(Font.BOLD));
    userName.setForeground(Styles.INK);
    account.add(userName);
    account.add(Box.createVerticalStrut(4));
    account.add(userRole);
    account.add(Box.createVerticalStrut(10));
    account.add(logout);
    sidebar.add(account, BorderLayout.SOUTH);
    add(sidebar, BorderLayout.WEST);
    content.add(catalog, "catalog");
    content.add(editor, "editor");
    content.add(masters, "masters");
    content.add(reports, "reports");
    content.add(audit, "audit");
    content.add(users, "users");
    content.add(connection, "connection");
    add(content);
    status.setBorder(new EmptyBorder(10, 18, 10, 18));
    add(status, BorderLayout.SOUTH);
    loggedOut();
  }

  /** Arma el menú con las opciones permitidas para el rol que devolvió la base. */
  public void applySession(Session s) {
    menu.removeAll();
    for (var entry : nav.entrySet()) {
      if (!s.admin() && ADMIN_ONLY.contains(entry.getKey())) continue;
      menu.add(entry.getValue());
      menu.add(Box.createVerticalStrut(10));
    }
    menu.revalidate();
    menu.repaint();
    userName.setText("<html><div style='width:140px'>" + s.name() + "</div></html>");
    userRole.setText("<html><div style='width:140px'>" + s.user() + "<br>Rol: " + s.role() + "</div></html>");
    account.setVisible(true);
    sidebar.setVisible(true);
    catalog.setAdmin(s.admin());
    status.setText("Sesión iniciada como " + s.user() + " (" + s.role() + ")");
  }

  public void loggedOut() {
    menu.removeAll();
    menu.revalidate();
    menu.repaint();
    account.setVisible(false);
    // Sin sesión no hay menú: se oculta la barra lateral para que el inicio de sesión quede
    // centrado en toda la ventana.
    sidebar.setVisible(false);
    revalidate();
    repaint();
    status.setText("Sin sesión");
    show("connection");
  }

  public void show(String card) {
    current = card;
    layout.show(content, card);
    for (var entry : nav.entrySet()) {
      entry.getValue().setBackground(entry.getKey().equals(card) ? Styles.SELECTED : Color.WHITE);
    }
  }

  public void busy(boolean value, String text) {
    status.setText(text);
    setCursor(Cursor.getPredefinedCursor(value ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    JRootPane root = SwingUtilities.getRootPane(this);
    if (root != null) {
      Component glass = root.getGlassPane();
      glass.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      glass.setVisible(value);
    }
  }
}
