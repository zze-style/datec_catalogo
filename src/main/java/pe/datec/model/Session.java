package pe.datec.model;

/** Usuario que inició sesión. El rol lo informa PostgreSQL (sp_sesion), no la aplicación. */
public record Session(String user, String name, String role) {
  public static final String ADMIN = "Administrador";

  public static Session from(DataTable t) {
    if (t.rows().isEmpty()) throw new IllegalStateException("No se pudo leer la sesión.");
    return new Session(t.text(0, "usuario"), t.text(0, "nombre"), t.text(0, "rol"));
  }

  public boolean admin() {
    return ADMIN.equals(role);
  }
}
