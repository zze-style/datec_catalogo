package pe.datec.controller;

import java.util.List;
import javax.swing.*;
import pe.datec.dao.*;
import pe.datec.model.*;
import pe.datec.service.*;
import pe.datec.view.*;

/** Traduce las acciones del catálogo en operaciones del modelo. */
public final class ProductController {
  private final MainView view;
  private final UiTasks tasks;
  private ProductDao dao;
  private ProductService service;
  private Product selected;
  private Product editing; // ficha tal como estaba al abrir el formulario
  private long selectionRequest;
  private boolean admin;

  public ProductController(MainView view, UiTasks tasks) {
    this.view = view;
    this.tasks = tasks;
    view.catalog.find.addActionListener(e -> refresh());
    view.catalog.search.addActionListener(e -> refresh());
    view.catalog.create.addActionListener(e -> open(null));
    view.catalog.edit.addActionListener(
        e -> {
          if (selected != null) open(selected);
        });
    view.catalog.remove.addActionListener(e -> delete());
    view.catalog
        .table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) select();
            });
    view.editor.cancel.addActionListener(
        e -> {
          if (confirmLeave()) {
            view.dirtyEditor = false;
            view.show("catalog");
          }
        });
    view.editor.save.addActionListener(e -> save());
  }

  public void connect(RoutineDao db, boolean admin) {
    dao = new ProductDao(db);
    service = new ProductService(dao);
    selected = null;
    this.admin = admin;
  }

  public void disconnect() {
    dao = null;
    service = null;
    selected = null;
    admin = false;
    selectionRequest++;
    view.catalog.display(DataTable.empty());
  }

  public boolean confirmLeave() {
    return !view.dirtyEditor
        || JOptionPane.showConfirmDialog(
                view,
                "¿Salir del formulario? Los cambios sin guardar se perderán.",
                "Volver",
                JOptionPane.YES_NO_OPTION)
            == JOptionPane.YES_OPTION;
  }

  public void refresh() {
    if (dao == null) {
      view.show("connection");
      return;
    }
    CatalogView c = view.catalog;
    String text = c.search.getText().trim();
    Integer category = Styles.selected(c.category), brand = Styles.selected(c.brand);
    String state =
        c.condition.getSelectedIndex() == 0 ? null : (String) c.condition.getSelectedItem();
    tasks.run(
        "Consultando catálogo",
        () -> dao.search(text, category, brand, state),
        result -> {
          selectionRequest++;
          selected = null;
          view.catalog.display(result);
        });
  }

  private void select() {
    if (dao == null) return;
    Integer id = view.catalog.selectedId();
    long request = ++selectionRequest;
    if (id == null) {
      selected = null;
      view.catalog.setSelected(null);
      return;
    }
    tasks.run(
        "Abriendo ficha",
        () -> dao.get(id),
        p -> {
          if (request == selectionRequest) {
            selected = p;
            view.catalog.setSelected(p);
          }
        });
  }

  private void open(Product p) {
    if (!admin) return;
    if (dao == null) {
      view.show("connection");
      return;
    }
    if (view.editor.brand.getItemCount() == 0
        || view.editor.category.getItemCount() == 0
        || view.editor.presentation.getItemCount() == 0) {
      tasks.error(
          new IllegalArgumentException("Registre primero una marca, categoría y presentación."));
      return;
    }
    editing = p;
    view.editor.open(p);
    view.dirtyEditor = true;
    view.show("editor");
  }

  private void save() {
    try {
      Product p = view.editor.value();
      ProductService.validateFields(p);
      List<String> before = editing == null ? List.of() : editing.images();
      CloudinaryStorageService cloud = CloudinaryStorageService.fromConfig();
      long pending = p.images().stream().filter(LocalImages::isPending).count();
      if (pending > 0 && !cloud.canUpload())
        throw new IllegalArgumentException(
            "Hay " + pending + " foto(s) pendientes de subir y Cloudinary no está configurado.\n"
                + "Use «Configurar Cloudinary…» en la pestaña Imágenes.");
      tasks.run(
          pending > 0 ? "Subiendo " + pending + " foto(s) a Cloudinary y guardando" : "Guardando ficha",
          () -> service.saveWithImages(p, before, cloud),
          result -> {
            view.dirtyEditor = false;
            view.show("catalog");
            refresh();
            if (!result.warnings().isEmpty())
              JOptionPane.showMessageDialog(
                  view,
                  "La ficha se guardó. Avisos:\n" + String.join("\n", result.warnings()),
                  "Guardado con avisos",
                  JOptionPane.INFORMATION_MESSAGE);
          });
    } catch (Exception e) {
      tasks.error(e);
    }
  }

  private void delete() {
    if (selected == null || !admin) return;
    Product p = selected;
    if (JOptionPane.showConfirmDialog(
            view,
            "¿Eliminar el producto "
                + p.code()
                + " · "
                + p.name()
                + "?\nTambién se eliminarán sus precios, imágenes y características."
                + "\nLa operación queda registrada en el historial.",
            "Eliminar producto",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE)
        != JOptionPane.YES_OPTION) return;
    tasks.run(
        "Eliminando producto",
        () -> {
          dao.delete(p.id());
          // La base ya eliminó el producto: se retiran sus fotos propias de Cloudinary.
          return ProductService.deleteCloudImages(p.images(), CloudinaryStorageService.fromConfig());
        },
        warnings -> {
          refresh();
          if (!warnings.isEmpty())
            JOptionPane.showMessageDialog(
                view, "Producto eliminado. Avisos:\n" + String.join("\n", warnings), "Eliminado con avisos",
                JOptionPane.INFORMATION_MESSAGE);
        });
  }
}
