package pe.datec.controller;

import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import pe.datec.dao.*;
import pe.datec.model.*;
import pe.datec.service.*;
import pe.datec.view.*;

/**
 * Coordina inicio de sesión, navegación por rol y los módulos de clasificaciones, reportes,
 * historial y usuarios. Toda regla de permisos se valida otra vez en PostgreSQL.
 */
public final class AppController {
  private final MainView view;
  private final UiTasks tasks;
  public final ProductController products;
  private RoutineDao db;
  private Session session;
  private DataTable masters = DataTable.empty(), audit = DataTable.empty(), users = DataTable.empty();
  private String loadedMasterType = "Marca";

  public AppController(MainView view) {
    this.view = view;
    tasks = new UiTasks(view);
    products = new ProductController(view, tasks);
    view.nav.forEach((name, button) -> button.addActionListener(e -> navigate(name)));
    view.connection.login.addActionListener(e -> login());
    view.connection.password.addActionListener(e -> login());
    view.connection.user.addActionListener(e -> view.connection.password.requestFocusInWindow());
    view.connection.configure.addActionListener(e -> configureServer());
    view.logout.addActionListener(e -> logout());
    view.masters.refresh.addActionListener(e -> loadMasters());
    view.masters.type.addActionListener(
        e -> {
          if (db != null) loadMasters();
        });
    view.masters.create.addActionListener(e -> editMaster(false));
    view.masters.edit.addActionListener(e -> editMaster(true));
    view.masters.remove.addActionListener(e -> removeMaster());
    view.reports.run.addActionListener(e -> report());
    view.reports.text.addActionListener(e -> report());
    view.reports.plan.addActionListener(e -> plan());
    view.reports.export.addActionListener(e -> export(view.reports.table));
    view.reports.list.addListSelectionListener(
        e -> {
          if (!e.getValueIsAdjusting()) {
            Styles.setData(view.reports.table, DataTable.empty());
            view.reports.export.setEnabled(false);
            view.reports.count.setText("Ajuste los filtros y pulse Consultar.");
          }
        });
    view.audit.find.addActionListener(e -> loadAudit());
    view.audit.search.addActionListener(e -> loadAudit());
    view.audit.export.addActionListener(e -> export(view.audit.table));
    view.audit
        .table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (e.getValueIsAdjusting()) return;
              int r = view.audit.table.getSelectedRow();
              if (r >= 0) {
                r = view.audit.table.convertRowIndexToModel(r);
                view.audit.detail.setText(
                    "Valores anteriores\n"
                        + audit.text(r, "anterior")
                        + "\n\nValores nuevos\n"
                        + audit.text(r, "nuevo"));
                view.audit.detail.setCaretPosition(0);
              }
            });
    view.users.refresh.addActionListener(e -> loadUsers());
    view.users.create.addActionListener(e -> editUser(false));
    view.users.edit.addActionListener(e -> editUser(true));
  }

  // ------------------------------------------------------------------ sesión

  private void login() {
    try {
      DbConfig c = view.connection.credentials();
      RoutineDao candidate = new RoutineDao(c);
      view.connection.message.setText(" ");
      tasks.run(
          "Iniciando sesión",
          () -> candidate.query("sp_sesion"),
          result -> {
            session = Session.from(result);
            db = candidate;
            view.connection.password.setText("");
            try {
              c.save(); // servidor y usuario; nunca la contraseña
            } catch (Exception ignored) {
            }
            products.connect(db, session.admin());
            view.applySession(session);
            navigate("catalog");
          });
    } catch (Exception e) {
      tasks.error(e);
    }
  }

  private void logout() {
    if (view.current.equals("editor") && !products.confirmLeave()) return;
    view.dirtyEditor = false;
    db = null;
    session = null;
    products.disconnect();
    masters = audit = users = DataTable.empty();
    for (JTable t : List.of(view.masters.table, view.reports.table, view.audit.table, view.users.table))
      Styles.setData(t, DataTable.empty());
    view.audit.detail.setText("");
    view.reports.export.setEnabled(false);
    view.audit.export.setEnabled(false);
    view.connection.load(DbConfig.load());
    view.loggedOut();
    view.connection.password.requestFocusInWindow();
  }

  /** Se usa solo la primera vez (o si cambia el servidor). Queda guardado en local.properties. */
  private void configureServer() {
    while (JOptionPane.showConfirmDialog(
            view,
            view.connection.serverForm(),
            "Configurar servidor PostgreSQL",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION) {
      try {
        DbConfig c = view.connection.serverValue();
        c.save();
        view.connection.load(c);
        view.connection.message.setText("Servidor guardado. Ya puede iniciar sesión.");
        return;
      } catch (Exception e) {
        tasks.error(e);
      }
    }
  }

  private boolean allowed(String screen) {
    return session != null && (session.admin() || !MainView.ADMIN_ONLY.contains(screen));
  }

  private void navigate(String name) {
    if (view.current.equals("editor") && !products.confirmLeave()) return;
    view.dirtyEditor = false;
    if (db == null) {
      view.show("connection");
      return;
    }
    if (!allowed(name)) return;
    view.show(name);
    switch (name) {
      case "catalog" -> loadLookups(products::refresh);
      case "masters" -> loadMasters();
      case "reports" -> loadReports();
      case "audit" -> loadAudit();
      case "users" -> loadUsers();
      default -> {}
    }
  }

  // ------------------------------------------------------------------ listas

  public void loadLookups(Runnable then) {
    if (db == null) return;
    RoutineDao current = db;
    tasks.run(
        "Actualizando listas",
        () -> {
          Map<String, DataTable> tables = new HashMap<>();
          for (String kind : List.of("Marca", "Categoria", "TipoPresentacion"))
            tables.put(kind, current.query("sp_maestros_listar", kind));
          tables.put("Productos", current.query("sp_catalogo", "", null, null, null));
          tables.put("Reportes", current.query("sp_reportes_indice"));
          return tables;
        },
        tables -> {
          List<Lookup> brands = lookup(tables.get("Marca")),
              categories = lookup(tables.get("Categoria")),
              presentations = lookup(tables.get("TipoPresentacion"));
          Styles.lookups(view.catalog.brand, brands, "Todas las marcas");
          Styles.lookups(view.catalog.category, categories, "Todas las categorías");
          Styles.lookups(view.editor.brand, brands, null);
          Styles.lookups(view.editor.category, categories, null);
          Styles.lookups(view.editor.presentation, presentations, null);
          Styles.lookups(view.reports.brand, brands, "Todas las marcas");
          Styles.lookups(view.reports.category, categories, "Todas las categorías");
          Styles.lookups(view.reports.presentation, presentations, "Todas las presentaciones");
          DataTable p = tables.get("Productos");
          List<Lookup> items = new ArrayList<>();
          for (int r = 0; r < p.rows().size(); r++)
            items.add(
                new Lookup(
                    p.number(r, "idproducto"),
                    p.text(r, "codigoproducto") + " · " + p.text(r, "nombreproducto")));
          Styles.lookups(view.reports.product, items, "Todos los productos");
          view.reports.setReports(Report.from(tables.get("Reportes")));
          then.run();
        });
  }

  private static List<Lookup> lookup(DataTable t) {
    List<Lookup> result = new ArrayList<>();
    for (int r = 0; r < t.rows().size(); r++)
      result.add(new Lookup(t.number(r, "id"), t.text(r, "nombre")));
    return result;
  }

  // ------------------------------------------------------------------ clasificaciones

  private void loadMasters() {
    if (db == null || !allowed("masters")) return;
    String kind = (String) view.masters.type.getSelectedItem();
    tasks.run(
        "Consultando clasificaciones",
        () -> db.query("sp_maestros_listar", kind),
        result -> {
          masters = result;
          loadedMasterType = kind;
          // La vista muestra nombre, descripción y uso. La abreviatura ya no se muestra.
          List<List<Object>> rows = new ArrayList<>();
          for (int r = 0; r < result.rows().size(); r++)
            rows.add(
                Arrays.asList(
                    result.get(r, "nombre"), result.get(r, "descripcion"), result.get(r, "productos")));
          Styles.setData(
              view.masters.table,
              new DataTable(List.of("Nombre", "Descripción", "Productos asociados"), rows));
          view.masters.count.setText(
              result.rows().size()
                  + " registros · Solo se pueden eliminar las clasificaciones con 0 productos"
                  + " asociados.");
        });
  }

  private int selectedMaster() {
    int r = view.masters.table.getSelectedRow();
    if (r < 0) throw new IllegalArgumentException("Seleccione un registro de la tabla.");
    return view.masters.table.convertRowIndexToModel(r);
  }

  private void editMaster(boolean editing) {
    if (db == null) return;
    try {
      String kind = (String) view.masters.type.getSelectedItem();
      int row = editing ? selectedMaster() : -1;
      if (editing && !kind.equals(loadedMasterType)) return;
      Integer id = editing ? masters.number(row, "id") : null;
      JTextField name = Styles.input(25);
      JTextArea description = new JTextArea(4, 25);
      description.setLineWrap(true);
      description.setWrapStyleWord(true);
      if (editing) {
        name.setText(masters.text(row, "nombre"));
        description.setText(masters.text(row, "descripcion"));
      }
      JPanel form = new JPanel(new GridLayout(0, 1, 0, 12));
      form.add(Styles.field("Nombre", name));
      form.add(Styles.field("Descripción (opcional)", Styles.scroll(description)));
      String label = MastersView.label(kind).toLowerCase();
      while (JOptionPane.showConfirmDialog(
              view,
              form,
              (editing ? "Modificar " : "Agregar ") + label,
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE)
          == JOptionPane.OK_OPTION) {
        int max = kind.equals("TipoPresentacion") ? 30 : 50;
        String n = name.getText().trim(), d = description.getText().trim();
        if (n.isEmpty() || n.length() > max) {
          JOptionPane.showMessageDialog(view, "El nombre es obligatorio (máximo " + max + " caracteres).");
          continue;
        }
        // La abreviatura de una presentación la genera la base de datos (sp_maestro_guardar).
        tasks.run(
            "Guardando clasificación",
            () -> db.execute("sp_maestro_guardar", kind, id, n, d, null),
            saved -> loadMasters());
        break;
      }
    } catch (Exception e) {
      tasks.error(e);
    }
  }

  private void removeMaster() {
    if (db == null) return;
    try {
      int r = selectedMaster();
      int id = masters.number(r, "id");
      String kind = loadedMasterType;
      if (JOptionPane.showConfirmDialog(
              view,
              "¿Eliminar " + masters.text(r, "nombre") + "?",
              "Eliminar clasificación",
              JOptionPane.YES_NO_OPTION)
          != JOptionPane.YES_OPTION) return;
      tasks.run(
          "Eliminando clasificación",
          () -> db.execute("sp_maestro_eliminar", kind, id),
          ignored -> loadMasters());
    } catch (Exception e) {
      tasks.error(e);
    }
  }

  // ------------------------------------------------------------------ reportes

  private void loadReports() {
    loadLookups(() -> {});
  }

  /** Parámetros comunes de sp_reporte y sp_reporte_plan, en el mismo orden que en SQL. */
  private Object[] reportArgs(Report r) {
    ReportsView v = view.reports;
    String text = r.uses("texto") ? v.text.getText().trim() : null;
    if (r.uses("texto") && text.isEmpty())
      throw new IllegalArgumentException("Escriba con qué texto debe comenzar la búsqueda.");
    BigDecimal min = r.uses("precio") ? amount(v.minimum.getText(), "desde") : null,
        max = r.uses("precio") ? amount(v.maximum.getText(), "hasta") : null;
    String state =
        r.uses("condicion") && v.condition.getSelectedIndex() > 0
            ? (String) v.condition.getSelectedItem()
            : null;
    return new Object[] {
      r.number(),
      text,
      r.uses("producto") ? Styles.selected(v.product) : null,
      r.uses("categoria") ? Styles.selected(v.category) : null,
      r.uses("marca") ? Styles.selected(v.brand) : null,
      state,
      r.uses("presentacion") ? Styles.selected(v.presentation) : null,
      min,
      max
    };
  }

  private static BigDecimal amount(String text, String which) {
    if (text == null || text.isBlank()) return null;
    try {
      BigDecimal value = new BigDecimal(text.trim().replace(',', '.'));
      if (value.signum() < 0) throw new NumberFormatException();
      return value;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Escriba el precio " + which + " como número, por ejemplo 50.00.");
    }
  }

  private void report() {
    if (db == null) return;
    Report r = view.reports.selected();
    if (r == null) return;
    try {
      Object[] args = reportArgs(r);
      tasks.run(
          "Generando reporte",
          () -> db.query("sp_reporte", args),
          result -> {
            Styles.setData(view.reports.table, result);
            view.reports.export.setEnabled(true);
            view.reports.count.setText(
                r.title() + " · " + result.rows().size() + " filas");
          });
    } catch (Exception e) {
      tasks.error(e);
    }
  }

  /** Muestra el EXPLAIN ANALYZE de la misma consulta e indica si aparece el índice esperado. */
  private void plan() {
    if (db == null) return;
    Report r = view.reports.selected();
    if (r == null) return;
    try {
      Object[] base = reportArgs(r);
      Object[] args = Arrays.copyOf(base, base.length + 1);
      boolean simulate = view.reports.simulate.isSelected();
      args[base.length] = simulate;
      tasks.run(
          "Analizando plan de ejecución",
          () -> db.query("sp_reporte_plan", args),
          result -> {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < result.rows().size(); i++) text.append(result.text(i, "plan")).append('\n');
            boolean used = text.indexOf(r.index()) >= 0;
            String verdict =
                used
                    ? "<html><b style='color:#1a7f37'>✔ PostgreSQL usa el índice " + r.index() + ".</b></html>"
                    : "<html><b style='color:#9a6700'>El plan no usa " + r.index() + ".</b><br>"
                        + "Con pocos registros leer la tabla completa (Seq Scan) es más barato."
                        + (simulate ? "" : "<br>Marque «Simular catálogo grande» para ver el índice.")
                        + "</html>";
            JTextArea area = new JTextArea(text.toString(), 18, 90);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JPanel panel = new JPanel(new BorderLayout(0, 10));
            panel.add(new JLabel(verdict), BorderLayout.NORTH);
            panel.add(Styles.scroll(area));
            panel.add(
                Styles.muted(
                    simulate
                        ? "Simulación activa: se desactivó el recorrido secuencial solo durante este análisis."
                        : "Plan real con los datos actuales (EXPLAIN ANALYZE)."),
                BorderLayout.SOUTH);
            JOptionPane.showMessageDialog(
                view, panel, "Plan de ejecución · " + r.title(), JOptionPane.PLAIN_MESSAGE);
          });
    } catch (Exception e) {
      tasks.error(e);
    }
  }

  // ------------------------------------------------------------------ historial

  private void loadAudit() {
    if (db == null || !allowed("audit")) return;
    String text = view.audit.search.getText().trim();
    tasks.run(
        "Consultando historial",
        () -> db.query("sp_bitacora", text),
        result -> {
          audit = result;
          List<String> columns =
              List.of("id", "fecha", "tabla", "registro", "operacion", "usuario_bd");
          List<List<Object>> rows = new ArrayList<>();
          for (int r = 0; r < result.rows().size(); r++) {
            List<Object> row = new ArrayList<>();
            for (String c : columns) row.add(result.get(r, c));
            rows.add(row);
          }
          Styles.setData(
              view.audit.table,
              new DataTable(List.of("N.°", "Fecha", "Tabla", "Registro", "Operación", "Usuario"), rows));
          view.audit.detail.setText("");
          view.audit.count.setText(
              result.rows().size() + " movimientos · Hasta 1000 resultados · Usuario que inició sesión");
          view.audit.export.setEnabled(true);
        });
  }

  // ------------------------------------------------------------------ usuarios

  private void loadUsers() {
    if (db == null || !allowed("users")) return;
    tasks.run(
        "Consultando usuarios",
        () -> db.query("sp_usuarios_listar"),
        result -> {
          users = result;
          List<List<Object>> rows = new ArrayList<>();
          for (int r = 0; r < result.rows().size(); r++)
            rows.add(
                Arrays.asList(
                    result.get(r, "usuario"),
                    result.get(r, "nombre"),
                    result.get(r, "rol"),
                    Boolean.TRUE.equals(result.get(r, "activo")) ? "Activo" : "Desactivado"));
          Styles.setData(view.users.table, new DataTable(List.of("Usuario", "Nombre", "Rol", "Estado"), rows));
          view.users.count.setText(result.rows().size() + " usuarios con rol DATEC");
        });
  }

  private void editUser(boolean editing) {
    if (db == null) return;
    try {
      int row = -1;
      if (editing) {
        row = view.users.table.getSelectedRow();
        if (row < 0) throw new IllegalArgumentException("Seleccione un usuario de la tabla.");
        row = view.users.table.convertRowIndexToModel(row);
      }
      JTextField user = Styles.input(20), name = Styles.input(26);
      JPasswordField password = new JPasswordField(20);
      password.setMargin(new Insets(7, 8, 7, 8));
      JComboBox<String> role = new JComboBox<>(new String[] {"Vendedor", "Administrador"});
      JCheckBox active = new JCheckBox("Puede iniciar sesión", true);
      if (editing) {
        user.setText(users.text(row, "usuario"));
        user.setEnabled(false);
        name.setText(users.text(row, "nombre"));
        role.setSelectedItem(users.text(row, "rol"));
        active.setSelected(Boolean.TRUE.equals(users.get(row, "activo")));
      }
      JPanel form = new JPanel(new GridLayout(0, 1, 0, 12));
      form.add(Styles.field("Usuario (minúsculas, sin espacios)", user));
      form.add(Styles.field("Nombre completo", name));
      form.add(Styles.field("Rol", role));
      form.add(
          Styles.field(editing ? "Nueva contraseña (vacío = no cambiar)" : "Contraseña (mín. 8)", password));
      form.add(active);
      if (JOptionPane.showConfirmDialog(
              view,
              form,
              editing ? "Modificar usuario" : "Nuevo usuario",
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE)
          != JOptionPane.OK_OPTION) return;
      char[] chars = password.getPassword();
      String secret = new String(chars);
      Arrays.fill(chars, '\0');
      String u = user.getText().trim(), n = name.getText().trim(), r = (String) role.getSelectedItem();
      boolean a = active.isSelected();
      tasks.run(
          "Guardando usuario",
          () -> db.execute("sp_usuario_guardar", u, n, secret, r, a),
          ignored -> loadUsers());
    } catch (Exception e) {
      tasks.error(e);
    }
  }

  // ------------------------------------------------------------------ exportar

  private void export(JTable table) {
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new java.io.File("datec-reporte.csv"));
    if (chooser.showSaveDialog(view) != JFileChooser.APPROVE_OPTION) return;
    Path file = chooser.getSelectedFile().toPath();
    if (!file.toString().toLowerCase().endsWith(".csv")) file = Path.of(file + ".csv");
    if (Files.exists(file)
        && JOptionPane.showConfirmDialog(
                view, "El archivo ya existe. ¿Reemplazarlo?", "Exportar", JOptionPane.YES_NO_OPTION)
            != JOptionPane.YES_OPTION) return;
    try {
      CsvExporter.export(table, file);
      view.status.setText("Reporte exportado: " + file);
    } catch (Exception e) {
      tasks.error(e);
    }
  }
}
