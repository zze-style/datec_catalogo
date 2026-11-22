package pe.datec.dao;

import java.sql.*;
import java.util.*;
import pe.datec.model.DataTable;

/**
 * Único punto JDBC. Ejecuta procedimientos con parámetros y materializa sus cursores en una
 * transacción.
 */
public final class RoutineDao {
  private final DbConfig config;

  public RoutineDao(DbConfig config) {
    config.validate();
    this.config = config;
  }

  private Connection open() throws SQLException {
    Properties p = new Properties();
    p.setProperty("user", config.user());
    p.setProperty("password", config.password());
    p.setProperty("connectTimeout", "5");
    p.setProperty("socketTimeout", "30");
    p.setProperty("ApplicationName", "DATEC Catalogo");
    p.setProperty("sslmode", config.sslmode());
    String host = config.host().contains(":") ? "[" + config.host() + "]" : config.host();
    return DriverManager.getConnection(
        "jdbc:postgresql://" + host + ":" + config.port() + "/" + config.database(), p);
  }

  private CallableStatement prepare(Connection c, String name, Object[] values, boolean cursor)
      throws SQLException {
    if (!name.matches("sp_[a-z_]+")) throw new IllegalArgumentException("Procedimiento no válido");
    List<String> placeholders = new ArrayList<>(Collections.nCopies(values.length, "?"));
    if (cursor) placeholders.set(values.length - 1, "CAST(? AS refcursor)");
    CallableStatement statement =
        c.prepareCall("CALL " + name + "(" + String.join(",", placeholders) + ")");
    statement.setQueryTimeout(25);
    for (int i = 0; i < values.length; i++) {
      Object value = values[i];
      if (value instanceof String[] array)
        statement.setArray(i + 1, c.createArrayOf("text", array));
      else if (value == null) statement.setNull(i + 1, Types.NULL);
      else statement.setObject(i + 1, value);
    }
    return statement;
  }

  public DataTable query(String name, Object... values) throws SQLException {
    String cursor = "datec_" + UUID.randomUUID().toString().replace("-", "");
    Object[] args = Arrays.copyOf(values, values.length + 1);
    args[values.length] = cursor;
    try (Connection c = open()) {
      c.setAutoCommit(false);
      try {
        try (CallableStatement call = prepare(c, name, args, true)) {
          call.execute();
        }
        DataTable result;
        // cursor es generado internamente y contiene solo letras, números y guion bajo.
        try (Statement fetch = c.createStatement()) {
          fetch.setQueryTimeout(25);
          try (ResultSet rs = fetch.executeQuery("FETCH ALL FROM \"" + cursor + "\"")) {
            result = read(rs);
          }
        }
        c.commit();
        return result;
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }

  public Integer execute(String name, Object... values) throws SQLException {
    try (Connection c = open()) {
      c.setAutoCommit(false);
      try (CallableStatement call = prepare(c, name, values, false)) {
        Integer id = null;
        if (call.execute())
          try (ResultSet rs = call.getResultSet()) {
            if (rs.next()) id = rs.getInt(1);
          }
        c.commit();
        return id;
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }

  private static DataTable read(ResultSet rs) throws SQLException {
    List<String> columns = new ArrayList<>();
    List<List<Object>> rows = new ArrayList<>();
    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++)
      columns.add(rs.getMetaData().getColumnLabel(i));
    while (rs.next()) {
      List<Object> row = new ArrayList<>();
      for (int i = 1; i <= columns.size(); i++) {
        Object value = rs.getObject(i);
        if (value instanceof java.sql.Array array) {
          value = Arrays.asList((Object[]) array.getArray());
          array.free();
        }
        row.add(value);
      }
      rows.add(Collections.unmodifiableList(row));
    }
    return new DataTable(List.copyOf(columns), List.copyOf(rows));
  }
}
