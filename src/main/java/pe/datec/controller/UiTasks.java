package pe.datec.controller;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.*;
import pe.datec.view.MainView;

/** Mantiene las consultas y la lectura de archivos fuera del hilo de eventos de Swing. */
public final class UiTasks {
  private final MainView view;
  private boolean running;

  public UiTasks(MainView view) {
    this.view = view;
  }

  public <T> void run(String title, Callable<T> action, Consumer<T> success) {
    if (running) return;
    running = true;
    view.busy(true, title + "…");
    new SwingWorker<T, Void>() {
      protected T doInBackground() throws Exception {
        return action.call();
      }

      protected void done() {
        running = false;
        view.busy(false, "Listo");
        try {
          success.accept(get());
        } catch (Exception e) {
          error(e);
        }
      }
    }.execute();
  }

  public void error(Throwable error) {
    while (error.getCause() != null
        && (error instanceof java.util.concurrent.ExecutionException
            || error instanceof java.lang.reflect.InvocationTargetException))
      error = error.getCause();
    String message = error.getMessage();
    if (error instanceof SQLException sql) {
      String state = sql.getSQLState();
      String server = serverText(message);
      message = server;
      if ("23505".equals(state))
        message = "Ya existe un registro con ese código o nombre. Use un valor diferente.";
      else if ("23503".equals(state))
        message =
            "La clasificación está vinculada a productos o ya no existe. Revise las referencias"
                + " antes de continuar.";
      else if ("23514".equals(state) || "23502".equals(state))
        message =
            "Hay un dato obligatorio o una regla de la base que no se cumple. Revise precios,"
                + " cantidades y campos vacíos.";
      else if ("22001".equals(state)) message = "Un campo supera la longitud permitida.";
      else if ("28P01".equals(state)) message = "Usuario o contraseña incorrectos.";
      else if ("28000".equals(state))
        message = "El usuario está desactivado o no tiene permiso para iniciar sesión.";
      else if ("42501".equals(state)) {
        // El texto nativo cambia según el idioma del servidor (inglés o español).
        String low = server == null ? "" : server.toLowerCase();
        boolean denied = low.startsWith("permission denied") || low.startsWith("permiso denegado");
        if (denied && (low.contains("database") || low.contains("base de datos")))
          message = "Este usuario no tiene un rol de DATEC: no puede entrar a la base del catálogo.";
        else if (denied)
          message = "PostgreSQL rechazó la operación: su rol no tiene permiso para realizarla.";
      }
      else if ("3D000".equals(state))
        message = "La base de datos no existe. Ejecute primero sql/instalar.sql.";
      else if ("42883".equals(state) || "42703".equals(state) || "42P01".equals(state))
        message =
            "Faltan tablas o procedimientos de DATEC. Ejecute 04_funcionalidad.sql,"
                + " 07_roles_reportes.sql y 09_triggers.sql en la base correcta.";
      else if (state != null && state.startsWith("08"))
        message =
            "No se pudo comunicar con PostgreSQL. Revise el servidor, puerto y que el servicio esté"
                + " iniciado (Configurar servidor…).";
    }
    if (message == null || message.isBlank()) message = "No se pudo completar la operación.";
    view.status.setText("Operación no completada");
    JOptionPane.showMessageDialog(view, message, "DATEC", JOptionPane.WARNING_MESSAGE);
  }

  /** Quita el prefijo "ERROR:" y el contexto PL/pgSQL del mensaje de PostgreSQL. */
  static String serverText(String message) {
    if (message == null) return null;
    String m = message.replaceFirst("^(ERROR|FATAL): ", "");
    int cut = m.indexOf('\n');
    return (cut >= 0 ? m.substring(0, cut) : m).trim();
  }
}
