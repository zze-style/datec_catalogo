# DATEC Catálogo

Aplicación de escritorio en **Java Swing, POO y MVC**, con PostgreSQL y JDBC. Administra el catálogo comercial de DATEC mediante procedimientos almacenados. Las siete tablas del modelo físico se conservan y se complementan con una bitácora de cambios.

## Abrir la aplicación

**Versión JAR:** con Java 17 o posterior, haga doble clic en `ejecutar.cmd`, o ejecute `java -jar dist/app/datec.jar` desde la carpeta del proyecto. El programa abre la pantalla de inicio de sesión. La contraseña se conserva solo en memoria durante la sesión.

La aplicación está probada con PostgreSQL 17 y se compila para Java 17. La distribución portable se generó con OpenJDK 25 para Windows x64.

## Abrir y ejecutar desde Eclipse IDE

El proyecto incluye `.project`, `.classpath`, `.settings` y `DATEC.launch`. El controlador JDBC ya está agregado al proyecto mediante una ruta relativa; no requiere Maven ni plugins adicionales.

1. En Eclipse IDE para Java, elija **File > Import > General > Existing Projects into Workspace > Next**.
2. En **Select root directory**, seleccione la carpeta del proyecto (la que contiene `.project`). Si utiliza el ZIP de fuentes, extráigalo primero. Marque el proyecto **DATEC** y pulse **Finish**. Deje **Copy projects into workspace** desmarcado para trabajar directamente sobre esta carpeta.
3. Compruebe que Eclipse utiliza un **JDK 17 o posterior** en **Window > Preferences > Java > Installed JREs**. Si falta, pulse **Add > Standard VM > Directory** y seleccione la carpeta de su JDK. Seleccione ese JDK y aplique los cambios. Si el proyecto usa otro entorno, cámbielo en **Project > Properties > Java Build Path > Libraries > JRE System Library > Edit**.
4. Abra `src/main/java > pe.datec > App.java`, haga clic derecho y elija **Run As > Java Application**. También puede hacer clic derecho en `DATEC.launch` y elegir **Run As > DATEC**.
5. Se abrirá la ventana Swing. Con PostgreSQL iniciado y los scripts cargados, inicie sesión con su usuario de DATEC.

La configuración compartida `DATEC.launch` fija la carpeta de trabajo en la raíz del proyecto para localizar `config`. Si crea otra configuración de ejecución, compruebe **Run > Run Configurations > Java Application > Arguments > Working directory** y seleccione la carpeta del proyecto.

Las carpetas `model`, `view`, `controller`, `service` y `dao` contienen el código editable. Ejecute `App.java` como entrada del programa. **Debug As > Java Application** permite usar puntos de interrupción.

Si aparecen errores del entorno Java, compruebe el JDK y use **Project > Clean**. Si no encuentra el controlador PostgreSQL, verifique que `lib/postgresql-42.7.14.jar` existe y figura en **Java Build Path > Libraries**.

Referencias: [importar proyectos existentes](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.user/tasks/tasks-importproject.htm) y [configurar Java Build Path](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.user/reference/ref-properties-build-path.htm).

## Instalar la base nueva

1. Inicie PostgreSQL y abra SQL Shell (psql), conectado a la base `postgres`, con un usuario que pueda crear bases de datos.
2. Ejecute, adaptando la ruta a la ubicación del proyecto:

```sql
\i 'C:/ruta/datec_catalogo/sql/instalar.sql'
```

También puede usar PowerShell:

```powershell
& 'C:\Program Files\PostgreSQL\17\bin\psql.exe' -X -U postgres -d postgres -f '.\sql\instalar.sql'
```

El instalador crea `datec_catalogo`, carga los 69 productos reales del inventario de DATEC (18/09/2026), y agrega los procedimientos, triggers, roles y usuarios de demostración. Ejecútelo como `postgres` (crear roles requiere superusuario o CREATEROLE). Los precios son los del inventario; no se cargan cantidades en almacén.

El instalador se detiene si la base ya existe. No borra bases existentes. Los comandos `\connect` y `\ir` pertenecen a psql; no se deben pegar los scripts completos en una ventana JDBC.

## Iniciar sesión y conexión

La conexión se configura **una sola vez**. Los valores por defecto están en el código fuente (`DbConfig`: `localhost`, `5432`, `datec_catalogo`), así que con PostgreSQL local no hay que configurar nada. Si el servidor es otro, pulse **Configurar servidor…** en la pantalla de inicio; queda guardado en `config/local.properties` (nunca se guarda una contraseña).

Después cada persona entra con **su usuario y contraseña**. Cada usuario de DATEC es un usuario de PostgreSQL que pertenece a uno de dos roles de grupo:

| Rol | Puede |
|---|---|
| `datec_vendedor` | Consultar productos, ver imágenes y fichas, ejecutar reportes y ver sus planes |
| `datec_administrador` | Todo lo anterior, más crear, editar y eliminar productos, clasificaciones, historial y usuarios |

Usuarios de demostración que crea `07_roles_reportes.sql` (cámbielos en **Usuarios**): `admin_datec` / `Admin.2026` y `vendedor_datec` / `Ventas.2026`. Un superusuario (`postgres`) entra como Administrador.

Los permisos los aplica **PostgreSQL** con `GRANT`/`REVOKE`, no solo la interfaz: un vendedor que intente modificar algo, aunque sea desde psql, recibe *permission denied*. La bitácora registra el usuario real que inició sesión.

## Triggers de reglas de negocio

`sql/09_triggers.sql` (incluido en `instalar.sql`; en una base existente ejecútelo como `postgres`) agrega, además de la auditoría: normalización de producto y características, validación de enlaces de imagen, coherencia de precios (mayorista menor que minorista, comprobada al final de la transacción) y protección de marcas, categorías y presentaciones en uso.

## Respaldos

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/respaldo.ps1 -Imagenes -IncluirRoles -CopiaExterna "E:\Respaldos DATEC"
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restaurar_prueba.ps1
```

El primero genera `respaldos/respaldo_datec_AAAA-MM-DD.dump` con `pg_dump -F c`, lo comprueba, aplica la retención (7 diarios, 4 semanales, 3 mensuales) y registra todo en `respaldos/historial_respaldos.csv`. El segundo restaura el último respaldo en `datec_prueba` y compara filas, procedimientos y triggers con la base original.

## Funciones incluidas

- Catálogo con búsquedas por nombre/modelo/código y filtros de marca, categoría y condición.
- Alta y edición de fichas completas: datos, precios minoristas/mayoristas, características e imágenes.
- Eliminación de productos junto con sus precios, imágenes y características, en una sola transacción y registrada en el historial.
- Altas, modificaciones y eliminación de marcas, categorías y presentaciones no utilizadas.
- Índice de diez reportes definido en la base (`sp_reportes_indice`); cada reporte se apoya en un índice de PostgreSQL y puede mostrar su plan de ejecución (`EXPLAIN ANALYZE`), con opción de simular un catálogo grande.
- Galería de imágenes con Cloudinary: fotos del equipo pendientes hasta guardar, subida y borrado en la nube coordinados con la base, enlaces de internet con vista previa, miniaturas y visor con flechas. La base guarda solo enlaces.
- Inicio de sesión con roles Administrador y Vendedor; pantalla de Usuarios para el administrador.
- Bitácora automática de las siete tablas, con búsqueda, valores anteriores/nuevos y exportación.
- Transacciones atómicas: si falla una parte del guardado, no se guarda nada.
- Lectura local/HTTP(S) de imágenes PNG/JPEG, límites de tamaño y manejo de imágenes ausentes.

El catálogo no representa stock, ventas, pedidos, compras ni movimientos de almacén. `Nuevo/Usado` describe la condición del artículo. El precio corresponde a la presentación comercial. El encargado determina el mínimo mayorista (2 o 3) porque el costo de adquisición no forma parte del modelo.

Las características tienen una entrada por línea. Las imágenes se registran **solo como enlaces (URL)**: la base guarda la URL en `ImagenProducto.imagen`, nunca el archivo, y no se agregó ninguna columna ni tabla. En la galería, **Agregar desde el equipo** (o arrastrar fotos) deja las fotos **pendientes**, con vista previa local. Al pulsar **Guardar cambios**, en segundo plano: (1) se suben a Cloudinary solo las pendientes, reducidas a 1600 px, en la carpeta `productos/<código>/`; (2) `sp_producto_guardar` guarda la lista final de URL en una transacción; (3) solo si la base guardó, se borran de Cloudinary las fotos propias que se quitaron. Si falla la base, no se borra nada y las fotos recién subidas se retiran. El `public_id` de Cloudinary se obtiene de la propia URL. **Agregar enlace** sirve para imágenes que ya están en internet. Las miniaturas se piden a Cloudinary con `w_300,c_limit,f_jpg` y «Ver en grande» con `f_jpg` (Java no abre WebP). La primera imagen, marcada con ★, es la principal.

**Configurar Cloudinary (una vez por equipo):** cree una cuenta gratuita en cloudinary.com; en Settings > Upload cree un *upload preset* en modo **Unsigned**. En la app: Editar ficha > Imágenes > **Configurar Cloudinary…** y escriba el *cloud name* y el preset. El *API key* y el *API secret* (Dashboard > API Keys) solo hacen falta para borrar de la nube las fotos quitadas; configúrelos solo en la PC del administrador. Todo se guarda en `config/cloudinary.properties` (excluido del repositorio; plantilla en `config/cloudinary.properties.example`), nunca en la base ni en el código.

La bitácora registra el usuario que inició sesión (cada persona tiene su propio usuario de PostgreSQL). Presenta hasta los últimos 1000 movimientos coincidentes.

## Estructura del proyecto

```text
src/main/java/pe/datec/
  App.java              Entrada y ventana principal
  model/                Objetos de datos y definición de reportes
  view/                 Pantallas y estilo Swing; sin SQL
  controller/           Eventos, navegación y tareas en segundo plano
  service/              Validaciones, imágenes, CSV y rutas
  dao/                  JDBC y llamadas a procedimientos
sql/
  instalar.sql                  Instalación completa (ejecuta 01, 02, 04, 07 y 09)
  01_creacion_bd.sql            Base, tablas e índices
  02_insercion_datos.sql        Datos del catálogo
  03_consultas_originales.sql   Diez consultas de demostración (se ejecuta aparte)
  04_funcionalidad.sql          Bitácora y procedimientos del catálogo
  07_roles_reportes.sql         Roles, usuarios, permisos y reportes
  09_triggers.sql               Triggers de reglas de negocio
lib/                    Controlador PostgreSQL y licencia
config/                 Valores iniciales de conexión y plantilla de Cloudinary
assets/logo/            Ícono del ejecutable
scripts/                Compilación, empaquetado y respaldos
```

## Compilar y empaquetar

Requiere un JDK 17 o posterior con `javac` y `jar` en PATH. Para el portable, también `jpackage`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/package.ps1
```

El segundo comando se detiene si ya existe `dist/portable/DATEC`, para no sobrescribir una instalación con configuración o imágenes. Mueva esa carpeta antes de generar una nueva versión. El conector JDBC ya está incluido y no se descarga durante la compilación. No se utiliza Maven, un ORM ni frameworks de aplicación.

## Dependencia externa

PostgreSQL JDBC 42.7.14, descargado de https://jdbc.postgresql.org/download/. Licencia en `lib/LICENSE-postgresql.txt`.

SHA-256 del JAR: `73914527305a40cce504b0d3d90b23caf565136912d607ae8ae7c5895512332c`.
