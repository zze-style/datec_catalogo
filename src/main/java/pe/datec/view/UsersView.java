package pe.datec.view;

import java.awt.*;
import javax.swing.*;

/** Administración de usuarios: solo visible para el rol Administrador. */
public final class UsersView extends JPanel {
  public final JButton create = Styles.button("Nuevo usuario", true),
      edit = Styles.button("Modificar", false),
      refresh = Styles.button("Actualizar", false);
  public final JTable table = Styles.table();
  public final JLabel count = Styles.muted(" ");

  public UsersView() {
    super(new BorderLayout());
    JPanel page = Styles.page();
    add(page);
    JPanel top = new JPanel(new GridLayout(0, 1, 0, 10));
    top.add(Styles.heading("Usuarios y roles"));
    top.add(
        Styles.muted(
            "Cada usuario es una cuenta de PostgreSQL que pertenece al rol datec_administrador o"
                + " datec_vendedor. Los permisos los aplica la base de datos."));
    top.add(Styles.row(create, edit, refresh));
    page.add(top, BorderLayout.NORTH);
    page.add(Styles.scroll(table));
    page.add(count, BorderLayout.SOUTH);
  }
}
