\set ON_ERROR_STOP on
\encoding UTF8
-- =====================================================================================
-- 07_roles_reportes.sql
-- Ejecutar con un SUPERUSUARIO (postgres) conectado a datec_catalogo, después de 04.
-- Es seguro ejecutarlo más de una vez.
--
--  1. Roles de PostgreSQL: datec_administrador y datec_vendedor (GRANT / REVOKE).
--  2. Usuarios de demostración: admin_datec y vendedor_datec.
--  3. Inicio de sesión y gestión de usuarios mediante procedimientos.
--  4. Reportes: cada reporte se apoya en un índice y puede mostrar su plan.
--  5. Clasificaciones: el listado ya no muestra la abreviatura; se genera sola.
-- =====================================================================================

BEGIN;

-- -------------------------------------------------------------------------------------
-- 1. Roles de grupo. Pertenecen al servidor; por eso se crean solo si no existen.
-- -------------------------------------------------------------------------------------
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'datec_administrador') THEN
  CREATE ROLE datec_administrador NOLOGIN;
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'datec_vendedor') THEN
  CREATE ROLE datec_vendedor NOLOGIN;
 END IF;
END $$;
COMMENT ON ROLE datec_administrador IS 'DATEC: mantiene catálogo, clasificaciones y usuarios';
COMMENT ON ROLE datec_vendedor IS 'DATEC: consulta el catálogo y los reportes';

-- Usuarios de demostración (cambie las contraseñas desde la pantalla Usuarios).
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'admin_datec') THEN
  CREATE ROLE admin_datec LOGIN PASSWORD 'Admin.2026' IN ROLE datec_administrador;
  COMMENT ON ROLE admin_datec IS 'Administrador de demostración';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vendedor_datec') THEN
  CREATE ROLE vendedor_datec LOGIN PASSWORD 'Ventas.2026' IN ROLE datec_vendedor;
  COMMENT ON ROLE vendedor_datec IS 'Vendedor de demostración';
 END IF;
END $$;

-- -------------------------------------------------------------------------------------
-- 2. Sesión: el rol se obtiene de PostgreSQL, no de la aplicación.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_rol_actual() RETURNS TEXT
LANGUAGE sql STABLE AS $$
 SELECT CASE
  WHEN (SELECT rolsuper FROM pg_roles WHERE rolname = session_user)
    OR pg_has_role(session_user, 'datec_administrador', 'MEMBER') THEN 'Administrador'
  WHEN pg_has_role(session_user, 'datec_vendedor', 'MEMBER') THEN 'Vendedor'
 END
$$;

CREATE OR REPLACE PROCEDURE sp_sesion(INOUT resultado REFCURSOR)
LANGUAGE plpgsql AS $$
BEGIN
 IF fn_rol_actual() IS NULL THEN
  RAISE EXCEPTION 'El usuario % no tiene un rol de DATEC asignado.', session_user USING ERRCODE = '42501';
 END IF;
 OPEN resultado FOR
  SELECT r.rolname::TEXT AS usuario,
         COALESCE(shobj_description(r.oid, 'pg_authid'), r.rolname) AS nombre,
         fn_rol_actual() AS rol
  FROM pg_roles r WHERE r.rolname = session_user;
END $$;

-- -------------------------------------------------------------------------------------
-- 3. Gestión de usuarios (solo administradores).
--    Cada usuario de la aplicación es un usuario de PostgreSQL miembro de un rol de grupo.
--    sp_usuario_guardar usa SECURITY DEFINER porque crear roles requiere CREATEROLE;
--    valida internamente que quien lo llama sea administrador.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_usuarios_listar(INOUT resultado REFCURSOR)
LANGUAGE plpgsql AS $$
BEGIN
 IF fn_rol_actual() IS DISTINCT FROM 'Administrador' THEN
  RAISE EXCEPTION 'Solo un administrador puede consultar los usuarios.' USING ERRCODE = '42501';
 END IF;
 OPEN resultado FOR
  SELECT r.rolname::TEXT AS usuario,
         COALESCE(shobj_description(r.oid, 'pg_authid'), '') AS nombre,
         CASE g.rolname WHEN 'datec_administrador' THEN 'Administrador' ELSE 'Vendedor' END AS rol,
         r.rolcanlogin AS activo
  FROM pg_roles r
  JOIN pg_auth_members am ON am.member = r.oid
  JOIN pg_roles g ON g.oid = am.roleid
  WHERE g.rolname IN ('datec_administrador', 'datec_vendedor')
  ORDER BY rol, usuario;
END $$;

CREATE OR REPLACE PROCEDURE sp_usuario_guardar(
 IN p_usuario TEXT, IN p_nombre TEXT, IN p_clave TEXT, IN p_rol TEXT, IN p_activo BOOLEAN)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, pg_temp AS $$
DECLARE
 v_oid OID;
 v_grupo TEXT := CASE p_rol WHEN 'Administrador' THEN 'datec_administrador' ELSE 'datec_vendedor' END;
 v_otro  TEXT := CASE p_rol WHEN 'Administrador' THEN 'datec_vendedor' ELSE 'datec_administrador' END;
BEGIN
 IF fn_rol_actual() IS DISTINCT FROM 'Administrador' THEN
  RAISE EXCEPTION 'Solo un administrador puede gestionar usuarios.' USING ERRCODE = '42501';
 END IF;
 p_usuario := lower(btrim(COALESCE(p_usuario, '')));
 p_activo := COALESCE(p_activo, TRUE);
 IF p_usuario !~ '^[a-z][a-z0-9_]{2,30}$' THEN
  RAISE EXCEPTION 'Usuario: de 3 a 31 caracteres (minúsculas, números o _), empezando por una letra.';
 END IF;
 IF p_usuario IN ('postgres', 'datec_administrador', 'datec_vendedor') OR p_usuario LIKE 'pg\_%' THEN
  RAISE EXCEPTION 'Ese nombre de usuario está reservado.';
 END IF;
 IF p_rol IS NULL OR p_rol NOT IN ('Administrador', 'Vendedor') THEN
  RAISE EXCEPTION 'El rol debe ser Administrador o Vendedor.';
 END IF;
 IF COALESCE(p_clave, '') <> '' AND length(p_clave) < 8 THEN
  RAISE EXCEPTION 'La contraseña debe tener al menos 8 caracteres.';
 END IF;

 SELECT oid INTO v_oid FROM pg_roles WHERE rolname = p_usuario;
 IF v_oid IS NULL THEN
  IF COALESCE(p_clave, '') = '' THEN RAISE EXCEPTION 'Escriba una contraseña para el nuevo usuario.'; END IF;
  EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', p_usuario, p_clave);
 ELSE
  IF (SELECT rolsuper FROM pg_roles WHERE oid = v_oid) OR NOT EXISTS (
     SELECT 1 FROM pg_auth_members am JOIN pg_roles g ON g.oid = am.roleid
     WHERE am.member = v_oid AND g.rolname IN ('datec_administrador', 'datec_vendedor')) THEN
   RAISE EXCEPTION 'El usuario % ya existe en el servidor y no pertenece a DATEC.', p_usuario;
  END IF;
  IF p_usuario = session_user AND (p_rol <> 'Administrador' OR NOT p_activo) THEN
   RAISE EXCEPTION 'No puede quitarse a sí mismo el acceso de administrador.';
  END IF;
  IF COALESCE(p_clave, '') <> '' THEN
   EXECUTE format('ALTER ROLE %I PASSWORD %L', p_usuario, p_clave);
  END IF;
 END IF;

 EXECUTE format('ALTER ROLE %I %s', p_usuario, CASE WHEN p_activo THEN 'LOGIN' ELSE 'NOLOGIN' END);
 IF pg_has_role(p_usuario, v_otro, 'MEMBER') THEN
  EXECUTE format('REVOKE %I FROM %I', v_otro, p_usuario);
 END IF;
 EXECUTE format('GRANT %I TO %I', v_grupo, p_usuario);
 EXECUTE format('COMMENT ON ROLE %I IS %L', p_usuario, NULLIF(btrim(COALESCE(p_nombre, '')), ''));

 INSERT INTO Bitacora(tabla, registro, operacion, usuario_bd, nuevo)
 VALUES ('usuario', (SELECT oid FROM pg_roles WHERE rolname = p_usuario)::INTEGER,
         CASE WHEN v_oid IS NULL THEN 'INSERT' ELSE 'UPDATE' END, session_user,
         jsonb_build_object('usuario', p_usuario, 'nombre', p_nombre, 'rol', p_rol,
                            'activo', p_activo, 'cambio_clave', COALESCE(p_clave, '') <> ''));
END $$;

-- -------------------------------------------------------------------------------------
-- 5. Clasificaciones: el listado ya no incluye la abreviatura y muestra cuántos
--    productos usan cada registro (explica por qué algunos no se pueden eliminar).
--    La columna abreviatura se conserva en TipoPresentacion (modelo físico del informe):
--    al crear una presentación se genera a partir del nombre y al modificarla se mantiene.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_maestros_listar(IN p_tipo TEXT, INOUT resultado REFCURSOR)
LANGUAGE plpgsql AS $$ BEGIN
 CASE p_tipo
 WHEN 'Marca' THEN OPEN resultado FOR
  SELECT m.idmarca AS id, m.nombremarca AS nombre, m.descripcion, COUNT(p.idproducto) AS productos
  FROM Marca m LEFT JOIN Producto p ON p.idmarca = m.idmarca GROUP BY m.idmarca ORDER BY nombre;
 WHEN 'Categoria' THEN OPEN resultado FOR
  SELECT c.idcategoria AS id, c.nombrecategoria AS nombre, c.descripcion, COUNT(p.idproducto) AS productos
  FROM Categoria c LEFT JOIN Producto p ON p.idcategoria = c.idcategoria GROUP BY c.idcategoria ORDER BY nombre;
 WHEN 'TipoPresentacion' THEN OPEN resultado FOR
  SELECT t.idtipopresentacion AS id, t.nombrepresentacion AS nombre, t.descripcion, COUNT(p.idproducto) AS productos
  FROM TipoPresentacion t LEFT JOIN Producto p ON p.idtipopresentacion = t.idtipopresentacion
  GROUP BY t.idtipopresentacion ORDER BY nombre;
 ELSE RAISE EXCEPTION 'Clasificación no válida'; END CASE;
END $$;

CREATE OR REPLACE PROCEDURE sp_maestro_guardar(IN p_tipo TEXT, INOUT p_id INTEGER, IN p_nombre TEXT, IN p_descripcion TEXT, IN p_abreviatura TEXT)
LANGUAGE plpgsql AS $$
DECLARE v_abrev TEXT := NULLIF(btrim(COALESCE(p_abreviatura, '')), '');
BEGIN
 IF btrim(COALESCE(p_nombre, '')) = '' THEN RAISE EXCEPTION 'El nombre es obligatorio'; END IF;
 p_descripcion := NULLIF(btrim(COALESCE(p_descripcion, '')), '');
 CASE p_tipo
 WHEN 'Marca' THEN
  IF p_id IS NULL THEN INSERT INTO Marca(nombremarca,descripcion) VALUES(btrim(p_nombre),p_descripcion) RETURNING idmarca INTO p_id;
  ELSE UPDATE Marca SET nombremarca=btrim(p_nombre),descripcion=p_descripcion WHERE idmarca=p_id; IF NOT FOUND THEN RAISE EXCEPTION 'La marca ya no existe'; END IF; END IF;
 WHEN 'Categoria' THEN
  IF p_id IS NULL THEN INSERT INTO Categoria(nombrecategoria,descripcion) VALUES(btrim(p_nombre),p_descripcion) RETURNING idcategoria INTO p_id;
  ELSE UPDATE Categoria SET nombrecategoria=btrim(p_nombre),descripcion=p_descripcion WHERE idcategoria=p_id; IF NOT FOUND THEN RAISE EXCEPTION 'La categoría ya no existe'; END IF; END IF;
 WHEN 'TipoPresentacion' THEN
  IF p_id IS NULL THEN
   INSERT INTO TipoPresentacion(nombrepresentacion,descripcion,abreviatura)
   VALUES(btrim(p_nombre),p_descripcion,COALESCE(v_abrev, left(split_part(lower(btrim(p_nombre)),' ',1),10)))
   RETURNING idtipopresentacion INTO p_id;
  ELSE
   UPDATE TipoPresentacion SET nombrepresentacion=btrim(p_nombre),descripcion=p_descripcion,
    abreviatura=COALESCE(v_abrev, abreviatura) WHERE idtipopresentacion=p_id;
   IF NOT FOUND THEN RAISE EXCEPTION 'La presentación ya no existe'; END IF;
  END IF;
 ELSE RAISE EXCEPTION 'Clasificación no válida'; END CASE;
END $$;

-- -------------------------------------------------------------------------------------
-- 6. Índices para búsquedas "comienza con". Los índices B-tree normales no sirven para
--    LIKE cuando la base usa una intercalación regional (Windows); text_pattern_ops sí.
-- -------------------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_producto_nombre_prefijo
 ON Producto (lower(nombreProducto) text_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_caracteristica_prefijo
 ON CaracteristicaProducto (lower(caracteristica) text_pattern_ops);

-- -------------------------------------------------------------------------------------
-- 7. Reportes. Cada reporte está asociado a un índice. Los parámetros siempre llegan
--    en el mismo orden ($1..$8) y el texto del reporte vive en una sola función, de
--    modo que el resultado y el plan de ejecución se calculan sobre la MISMA consulta.
--    $1 patrón de texto · $2 producto · $3 categoría · $4 marca · $5 condición
--    $6 presentación · $7 precio mínimo · $8 precio máximo
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE sp_reportes_indice(INOUT resultado REFCURSOR)
LANGUAGE plpgsql AS $$ BEGIN
 OPEN resultado FOR
 SELECT * FROM (VALUES
  (1, 'Clasificación', 'Productos por categoría',
      'Lista los productos de una categoría con su marca, condición y precio minorista.',
      'idx_producto_categoria', 'Producto(idCategoria)', ARRAY['categoria']),
  (2, 'Clasificación', 'Productos por marca',
      'Lista los productos de una marca con su categoría y precio minorista.',
      'idx_producto_marca', 'Producto(idMarca)', ARRAY['marca']),
  (3, 'Clasificación', 'Productos por presentación',
      'Muestra qué productos se venden por unidad, caja o pack.',
      'idx_producto_presentacion', 'Producto(idTipoPresentacion)', ARRAY['presentacion']),
  (4, 'Búsqueda', 'Buscar por nombre (comienza con…)',
      'Encuentra productos cuyo nombre empieza con el texto escrito, sin distinguir mayúsculas.',
      'idx_producto_nombre_prefijo', 'Producto(lower(nombreProducto))', ARRAY['texto']),
  (5, 'Búsqueda', 'Buscar por característica (comienza con…)',
      'Encuentra productos por su ficha técnica, por ejemplo «Capacidad» o «Color: negro».',
      'idx_caracteristica_prefijo', 'CaracteristicaProducto(lower(caracteristica))', ARRAY['texto']),
  (6, 'Precios', 'Productos en un rango de precios',
      'Tarifas minoristas y mayoristas entre un precio mínimo y uno máximo, ordenadas por monto.',
      'idx_precio_monto', 'PrecioProducto(monto)', ARRAY['precio']),
  (7, 'Precios', 'Tarifas de un producto',
      'Precio minorista y mayorista de un producto, con la cantidad mínima de cada tarifa.',
      'precioproducto_idproducto_tipoprecio_key', 'PrecioProducto(idProducto, tipoPrecio)', ARRAY['producto']),
  (8, 'Estado e imágenes', 'Productos nuevos o usados',
      'Separa el catálogo según la condición del artículo.',
      'idx_producto_estado', 'Producto(estadoProducto)', ARRAY['condicion']),
  (9, 'Estado e imágenes', 'Imágenes por producto',
      'Cuántas imágenes tiene cada producto; identifica los que aún no tienen ninguna.',
      'idx_imagen_producto', 'ImagenProducto(idProducto)', ARRAY[]::TEXT[]),
  (10, 'Resumen', 'Indicadores por categoría',
      'Totales de productos, nuevos y usados, y precios minoristas mínimo, promedio y máximo.',
      'idx_producto_categoria', 'Producto(idCategoria)', ARRAY[]::TEXT[])
 ) AS r(numero, grupo, titulo, descripcion, indice, columna, filtros)
 ORDER BY numero;
END $$;

CREATE OR REPLACE FUNCTION fn_reporte_sql(p_num INTEGER) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE AS $$ BEGIN
 RETURN CASE p_num
 WHEN 1 THEN $q$
  SELECT c.nombrecategoria AS "Categoría", p.codigoproducto AS "Código", p.nombreproducto AS "Producto",
         m.nombremarca AS "Marca", p.estadoproducto AS "Condición", mi.monto AS "Minorista S/"
  FROM Producto p JOIN Categoria c USING (idcategoria) JOIN Marca m USING (idmarca)
  LEFT JOIN PrecioProducto mi ON mi.idproducto = p.idproducto AND mi.tipoprecio = 'Minorista'
  WHERE ($3::INTEGER IS NULL OR p.idcategoria = $3)
  ORDER BY c.nombrecategoria, p.nombreproducto $q$
 WHEN 2 THEN $q$
  SELECT m.nombremarca AS "Marca", p.codigoproducto AS "Código", p.nombreproducto AS "Producto",
         c.nombrecategoria AS "Categoría", p.estadoproducto AS "Condición", mi.monto AS "Minorista S/"
  FROM Producto p JOIN Marca m USING (idmarca) JOIN Categoria c USING (idcategoria)
  LEFT JOIN PrecioProducto mi ON mi.idproducto = p.idproducto AND mi.tipoprecio = 'Minorista'
  WHERE ($4::INTEGER IS NULL OR p.idmarca = $4)
  ORDER BY m.nombremarca, p.nombreproducto $q$
 WHEN 3 THEN $q$
  SELECT t.nombrepresentacion AS "Presentación", p.codigoproducto AS "Código", p.nombreproducto AS "Producto",
         m.nombremarca AS "Marca", mi.monto AS "Minorista S/"
  FROM Producto p JOIN TipoPresentacion t USING (idtipopresentacion) JOIN Marca m USING (idmarca)
  LEFT JOIN PrecioProducto mi ON mi.idproducto = p.idproducto AND mi.tipoprecio = 'Minorista'
  WHERE ($6::INTEGER IS NULL OR p.idtipopresentacion = $6)
  ORDER BY t.nombrepresentacion, p.nombreproducto $q$
 WHEN 4 THEN $q$
  SELECT p.codigoproducto AS "Código", p.nombreproducto AS "Producto", p.modelo AS "Modelo",
         m.nombremarca AS "Marca", c.nombrecategoria AS "Categoría", p.estadoproducto AS "Condición"
  FROM Producto p JOIN Marca m USING (idmarca) JOIN Categoria c USING (idcategoria)
  WHERE lower(p.nombreproducto) LIKE $1::TEXT
  ORDER BY p.nombreproducto $q$
 WHEN 5 THEN $q$
  SELECT p.codigoproducto AS "Código", p.nombreproducto AS "Producto", cp.caracteristica AS "Característica",
         m.nombremarca AS "Marca"
  FROM CaracteristicaProducto cp JOIN Producto p USING (idproducto) JOIN Marca m USING (idmarca)
  WHERE lower(cp.caracteristica) LIKE $1::TEXT
  ORDER BY cp.caracteristica, p.nombreproducto $q$
 WHEN 6 THEN $q$
  SELECT pr.monto AS "Precio S/", pr.tipoprecio AS "Tarifa", pr.cantidadminima AS "Desde (presentaciones)",
         p.codigoproducto AS "Código", p.nombreproducto AS "Producto", m.nombremarca AS "Marca"
  FROM PrecioProducto pr JOIN Producto p USING (idproducto) JOIN Marca m USING (idmarca)
  WHERE pr.monto BETWEEN COALESCE($7::NUMERIC, 0) AND COALESCE($8::NUMERIC, 99999999.99)
  ORDER BY pr.monto, p.nombreproducto $q$
 WHEN 7 THEN $q$
  SELECT p.codigoproducto AS "Código", p.nombreproducto AS "Producto", t.nombrepresentacion AS "Presentación",
         pr.tipoprecio AS "Tarifa", pr.monto AS "Precio S/", pr.cantidadminima AS "Desde (presentaciones)"
  FROM Producto p JOIN TipoPresentacion t USING (idtipopresentacion) JOIN PrecioProducto pr USING (idproducto)
  WHERE ($2::INTEGER IS NULL OR pr.idproducto = $2)
  ORDER BY p.nombreproducto, pr.cantidadminima $q$
 WHEN 8 THEN $q$
  SELECT p.estadoproducto AS "Condición", p.codigoproducto AS "Código", p.nombreproducto AS "Producto",
         m.nombremarca AS "Marca", c.nombrecategoria AS "Categoría"
  FROM Producto p JOIN Marca m USING (idmarca) JOIN Categoria c USING (idcategoria)
  WHERE ($5::TEXT IS NULL OR p.estadoproducto = $5)
  ORDER BY p.estadoproducto, p.nombreproducto $q$
 WHEN 9 THEN $q$
  SELECT p.codigoproducto AS "Código", p.nombreproducto AS "Producto", COUNT(i.idimagen) AS "Imágenes",
         CASE WHEN COUNT(i.idimagen) = 0 THEN 'Sin imágenes'
              WHEN COUNT(i.idimagen) < 3 THEN 'Menos de 3 (recomendado: 3)'
              ELSE 'Completo' END AS "Situación"
  FROM Producto p LEFT JOIN ImagenProducto i ON i.idproducto = p.idproducto
  GROUP BY p.idproducto
  ORDER BY COUNT(i.idimagen), p.nombreproducto $q$
 WHEN 10 THEN $q$
  SELECT c.nombrecategoria AS "Categoría", COUNT(p.idproducto) AS "Productos",
         COUNT(p.idproducto) FILTER (WHERE p.estadoproducto = 'Nuevo') AS "Nuevos",
         COUNT(p.idproducto) FILTER (WHERE p.estadoproducto = 'Usado') AS "Usados",
         MIN(mi.monto) AS "Minorista mín. S/", ROUND(AVG(mi.monto), 2) AS "Minorista prom. S/",
         MAX(mi.monto) AS "Minorista máx. S/"
  FROM Categoria c
  LEFT JOIN Producto p ON p.idcategoria = c.idcategoria
  LEFT JOIN PrecioProducto mi ON mi.idproducto = p.idproducto AND mi.tipoprecio = 'Minorista'
  GROUP BY c.idcategoria
  ORDER BY COUNT(p.idproducto) DESC, c.nombrecategoria $q$
 END;
END $$;

-- Convierte el texto escrito en un patrón LIKE "comienza con" seguro (escapa % y _).
CREATE OR REPLACE FUNCTION fn_patron_prefijo(p_texto TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
 SELECT replace(replace(replace(lower(btrim(COALESCE(p_texto, ''))), '\', '\\'), '%', '\%'), '_', '\_') || '%'
$$;

CREATE OR REPLACE PROCEDURE sp_reporte(
 IN p_num INTEGER, IN p_texto TEXT, IN p_producto INTEGER, IN p_categoria INTEGER, IN p_marca INTEGER,
 IN p_estado TEXT, IN p_presentacion INTEGER, IN p_minimo NUMERIC, IN p_maximo NUMERIC,
 INOUT resultado REFCURSOR)
LANGUAGE plpgsql AS $$
DECLARE v_sql TEXT := fn_reporte_sql(p_num);
BEGIN
 IF v_sql IS NULL THEN RAISE EXCEPTION 'Reporte no válido'; END IF;
 IF p_minimo > p_maximo THEN RAISE EXCEPTION 'El precio mínimo no puede ser mayor que el máximo.'; END IF;
 OPEN resultado FOR EXECUTE v_sql
  USING fn_patron_prefijo(p_texto), p_producto, p_categoria, p_marca, p_estado, p_presentacion, p_minimo, p_maximo;
END $$;

-- Plan de ejecución real (EXPLAIN ANALYZE) de la misma consulta del reporte.
-- Con pocos registros PostgreSQL prefiere leer la tabla completa (Seq Scan) porque es más
-- barato. p_simular_volumen desactiva ese recorrido SOLO en esta transacción para mostrar
-- qué índice usaría el motor cuando la tabla crezca.
CREATE OR REPLACE PROCEDURE sp_reporte_plan(
 IN p_num INTEGER, IN p_texto TEXT, IN p_producto INTEGER, IN p_categoria INTEGER, IN p_marca INTEGER,
 IN p_estado TEXT, IN p_presentacion INTEGER, IN p_minimo NUMERIC, IN p_maximo NUMERIC,
 IN p_simular_volumen BOOLEAN, INOUT resultado REFCURSOR)
LANGUAGE plpgsql AS $$
DECLARE v_sql TEXT := fn_reporte_sql(p_num); v_linea TEXT; v_plan TEXT[] := ARRAY[]::TEXT[];
BEGIN
 IF v_sql IS NULL THEN RAISE EXCEPTION 'Reporte no válido'; END IF;
 IF p_simular_volumen THEN
  PERFORM set_config('enable_seqscan', 'off', true);
 END IF;
 FOR v_linea IN EXECUTE 'EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF) ' || v_sql
  USING fn_patron_prefijo(p_texto), p_producto, p_categoria, p_marca, p_estado, p_presentacion, p_minimo, p_maximo
 LOOP
  v_plan := v_plan || v_linea;
 END LOOP;
 OPEN resultado FOR SELECT u.linea AS plan FROM unnest(v_plan) WITH ORDINALITY AS u(linea, n) ORDER BY u.n;
END $$;

-- -------------------------------------------------------------------------------------
-- 8. Permisos. Nadie usa las tablas por defecto (PUBLIC); cada rol recibe solo lo suyo.
-- -------------------------------------------------------------------------------------
DO $$ BEGIN
 EXECUTE format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database());
 EXECUTE format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO datec_administrador, datec_vendedor', current_database());
END $$;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO datec_administrador, datec_vendedor;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;
REVOKE EXECUTE ON ALL ROUTINES IN SCHEMA public FROM PUBLIC;

-- Vendedor: solo lectura del catálogo y reportes.
GRANT SELECT ON Marca, Categoria, TipoPresentacion, Producto, PrecioProducto, ImagenProducto, CaracteristicaProducto
 TO datec_vendedor;
GRANT EXECUTE ON FUNCTION fn_rol_actual(), fn_reporte_sql(INTEGER), fn_patron_prefijo(TEXT) TO datec_vendedor;
GRANT EXECUTE ON PROCEDURE
 sp_sesion(REFCURSOR),
 sp_catalogo(TEXT, INTEGER, INTEGER, TEXT, REFCURSOR),
 sp_producto_detalle(INTEGER, REFCURSOR),
 sp_maestros_listar(TEXT, REFCURSOR),
 sp_reportes_indice(REFCURSOR),
 sp_reporte(INTEGER, TEXT, INTEGER, INTEGER, INTEGER, TEXT, INTEGER, NUMERIC, NUMERIC, REFCURSOR),
 sp_reporte_plan(INTEGER, TEXT, INTEGER, INTEGER, INTEGER, TEXT, INTEGER, NUMERIC, NUMERIC, BOOLEAN, REFCURSOR)
 TO datec_vendedor;

-- Administrador: mantenimiento completo. La bitácora solo se lee y se agrega (no se edita).
GRANT SELECT, INSERT, UPDATE, DELETE
 ON Marca, Categoria, TipoPresentacion, Producto, PrecioProducto, ImagenProducto, CaracteristicaProducto
 TO datec_administrador;
GRANT SELECT, INSERT ON Bitacora TO datec_administrador;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO datec_administrador;
GRANT EXECUTE ON ALL ROUTINES IN SCHEMA public TO datec_administrador;

COMMIT;
