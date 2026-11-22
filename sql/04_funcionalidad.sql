\set ON_ERROR_STOP on
\encoding UTF8
-- Ejecutar conectado a datec_catalogo. Agrega la bitácora y los procedimientos.
-- Las siete tablas del modelo físico NO se modifican.
BEGIN;
CREATE TABLE IF NOT EXISTS Bitacora (
 id BIGSERIAL PRIMARY KEY, fecha TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 tabla TEXT NOT NULL, registro INTEGER NOT NULL, operacion TEXT NOT NULL,
 usuario_bd TEXT NOT NULL DEFAULT session_user, anterior JSONB, nuevo JSONB
);
CREATE INDEX IF NOT EXISTS idx_bitacora_fecha ON Bitacora(fecha DESC);

-- Una función de trigger compartida audita las siete tablas del catálogo.
CREATE OR REPLACE FUNCTION fn_auditar() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE a JSONB; n JSONB; clave INTEGER; accion TEXT := TG_OP;
BEGIN
 IF TG_OP <> 'INSERT' THEN a := to_jsonb(OLD); END IF;
 IF TG_OP <> 'DELETE' THEN n := to_jsonb(NEW); END IF;
 clave := (COALESCE(n,a)->>TG_ARGV[0])::INTEGER;
 IF a IS DISTINCT FROM n THEN
  INSERT INTO Bitacora(tabla,registro,operacion,anterior,nuevo) VALUES(TG_TABLE_NAME,clave,accion,a,n);
 END IF;
 RETURN NULL;
END $$;
DO $$
DECLARE t TEXT; k TEXT;
BEGIN
 FOR t,k IN SELECT * FROM (VALUES ('marca','idmarca'),('categoria','idcategoria'),
 ('tipopresentacion','idtipopresentacion'),('producto','idproducto'),('precioproducto','idprecio'),
 ('imagenproducto','idimagen'),('caracteristicaproducto','idcaracteristica')) AS x(t,k) LOOP
  EXECUTE format('DROP TRIGGER IF EXISTS tr_auditoria ON %I',t);
  EXECUTE format('CREATE TRIGGER tr_auditoria AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION fn_auditar(%L)',t,k);
 END LOOP;
END $$;

CREATE OR REPLACE PROCEDURE sp_maestro_eliminar(IN p_tipo TEXT, IN p_id INTEGER)
LANGUAGE plpgsql AS $$ BEGIN
 CASE p_tipo
 WHEN 'Marca' THEN DELETE FROM Marca WHERE idmarca=p_id;
 WHEN 'Categoria' THEN DELETE FROM Categoria WHERE idcategoria=p_id;
 WHEN 'TipoPresentacion' THEN DELETE FROM TipoPresentacion WHERE idtipopresentacion=p_id;
 ELSE RAISE EXCEPTION 'Clasificación no válida'; END CASE;
 IF NOT FOUND THEN RAISE EXCEPTION 'El registro ya no existe'; END IF;
 -- ON DELETE RESTRICT impide eliminar clasificaciones que todavía usa algún producto.
END $$;

CREATE OR REPLACE PROCEDURE sp_catalogo(IN p_texto TEXT, IN p_categoria INTEGER, IN p_marca INTEGER, IN p_estado TEXT, INOUT resultado REFCURSOR)
LANGUAGE plpgsql AS $$ BEGIN
 OPEN resultado FOR
 SELECT p.*,m.nombremarca,c.nombrecategoria,t.nombrepresentacion,
  mi.monto AS minorista,ma.monto AS mayorista,ma.cantidadminima,
  (SELECT COUNT(*) FROM ImagenProducto i WHERE i.idproducto=p.idproducto) AS imagenes
 FROM Producto p JOIN Marca m USING(idmarca) JOIN Categoria c USING(idcategoria)
 JOIN TipoPresentacion t USING(idtipopresentacion)
 LEFT JOIN PrecioProducto mi ON mi.idproducto=p.idproducto AND mi.tipoprecio='Minorista'
 LEFT JOIN PrecioProducto ma ON ma.idproducto=p.idproducto AND ma.tipoprecio='Mayorista'
 WHERE (p_categoria IS NULL OR p.idcategoria=p_categoria) AND (p_marca IS NULL OR p.idmarca=p_marca)
 AND (p_estado IS NULL OR p.estadoproducto=p_estado)
 AND (COALESCE(p_texto,'')='' OR concat_ws(' ',p.nombreproducto,p.modelo,p.codigoproducto) ILIKE '%'||p_texto||'%')
 ORDER BY p.nombreproducto,p.codigoproducto;
END $$;

CREATE OR REPLACE PROCEDURE sp_producto_detalle(IN p_id INTEGER, INOUT resultado REFCURSOR)
LANGUAGE plpgsql AS $$ BEGIN
 OPEN resultado FOR SELECT p.*,
  (SELECT monto FROM PrecioProducto WHERE idproducto=p.idproducto AND tipoprecio='Minorista') AS minorista,
  (SELECT monto FROM PrecioProducto WHERE idproducto=p.idproducto AND tipoprecio='Mayorista') AS mayorista,
  (SELECT cantidadminima FROM PrecioProducto WHERE idproducto=p.idproducto AND tipoprecio='Mayorista') AS cantidadminima,
  ARRAY(SELECT caracteristica FROM CaracteristicaProducto WHERE idproducto=p.idproducto ORDER BY idcaracteristica) AS caracteristicas,
  ARRAY(SELECT imagen FROM ImagenProducto WHERE idproducto=p.idproducto ORDER BY idimagen) AS imagenes
 FROM Producto p WHERE p.idproducto=p_id;
END $$;

-- La ficha, sus tarifas y sus listas se guardan como una única operación atómica.
CREATE OR REPLACE PROCEDURE sp_producto_guardar(
 INOUT p_id INTEGER, IN p_codigo TEXT, IN p_nombre TEXT, IN p_modelo TEXT,
 IN p_descripcion TEXT, IN p_marca INTEGER, IN p_categoria INTEGER, IN p_presentacion INTEGER,
 IN p_estado TEXT, IN p_minorista NUMERIC, IN p_mayorista NUMERIC, IN p_minimo INTEGER,
 IN p_caracteristicas TEXT[], IN p_imagenes TEXT[])
LANGUAGE plpgsql AS $$ BEGIN
 IF p_minorista IS NULL THEN RAISE EXCEPTION 'Debe registrar el precio minorista'; END IF;
 -- ImagenProducto guarda solo enlaces (URL): nunca el archivo ni rutas de un equipo.
 IF EXISTS (SELECT 1 FROM unnest(COALESCE(p_imagenes,ARRAY[]::TEXT[])) x WHERE x !~* '^https?://[^[:space:]]+$' OR length(x) > 2048) THEN
  RAISE EXCEPTION 'Cada imagen debe ser un enlace que empiece con http:// o https://.';
 END IF;
 IF p_id IS NULL THEN
  INSERT INTO Producto(idmarca,idcategoria,idtipopresentacion,estadoproducto,nombreproducto,descripcion,modelo,codigoproducto)
  VALUES(p_marca,p_categoria,p_presentacion,p_estado,btrim(p_nombre),p_descripcion,NULLIF(btrim(p_modelo),''),btrim(p_codigo)) RETURNING idproducto INTO p_id;
 ELSE
  UPDATE Producto SET idmarca=p_marca,idcategoria=p_categoria,idtipopresentacion=p_presentacion,
   estadoproducto=p_estado,nombreproducto=btrim(p_nombre),descripcion=p_descripcion,
   modelo=NULLIF(btrim(p_modelo),''),codigoproducto=btrim(p_codigo)
  WHERE idproducto=p_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'El producto ya no existe. Actualice el catálogo.'; END IF;
 END IF;
 INSERT INTO PrecioProducto(idproducto,tipoprecio,monto,cantidadminima) VALUES(p_id,'Minorista',p_minorista,1)
 ON CONFLICT(idproducto,tipoprecio) DO UPDATE SET monto=EXCLUDED.monto,cantidadminima=1
 WHERE PrecioProducto.monto IS DISTINCT FROM EXCLUDED.monto;
 IF p_mayorista IS NULL THEN DELETE FROM PrecioProducto WHERE idproducto=p_id AND tipoprecio='Mayorista';
 ELSE
  INSERT INTO PrecioProducto(idproducto,tipoprecio,monto,cantidadminima) VALUES(p_id,'Mayorista',p_mayorista,p_minimo)
  ON CONFLICT(idproducto,tipoprecio) DO UPDATE SET monto=EXCLUDED.monto,cantidadminima=EXCLUDED.cantidadminima
  WHERE (PrecioProducto.monto,PrecioProducto.cantidadminima) IS DISTINCT FROM (EXCLUDED.monto,EXCLUDED.cantidadminima);
 END IF;
 DELETE FROM CaracteristicaProducto WHERE idproducto=p_id AND NOT (caracteristica=ANY(COALESCE(p_caracteristicas,ARRAY[]::TEXT[])));
 INSERT INTO CaracteristicaProducto(idproducto,caracteristica)
 SELECT p_id,x FROM unnest(COALESCE(p_caracteristicas,ARRAY[]::TEXT[])) x ON CONFLICT(idproducto,caracteristica) DO NOTHING;
 DELETE FROM ImagenProducto WHERE idproducto=p_id AND NOT (imagen=ANY(COALESCE(p_imagenes,ARRAY[]::TEXT[])));
 -- Las imágenes nuevas se insertan en el orden de la galería (idImagen define el orden; la primera es la principal).
 INSERT INTO ImagenProducto(idproducto,imagen)
 SELECT p_id,u.x FROM unnest(COALESCE(p_imagenes,ARRAY[]::TEXT[])) WITH ORDINALITY AS u(x,n)
 WHERE NOT EXISTS(SELECT 1 FROM ImagenProducto i WHERE i.idproducto=p_id AND i.imagen=u.x)
   AND u.n=(SELECT min(v.n) FROM unnest(p_imagenes) WITH ORDINALITY AS v(y,n) WHERE v.y=u.x)
 ORDER BY u.n;
END $$;

-- Elimina el producto y, en la misma transacción, sus precios, imágenes y características.
-- Las claves foráneas usan ON DELETE RESTRICT, por eso los registros dependientes se borran
-- primero y de forma explícita. La bitácora conserva una copia de cada fila eliminada.
CREATE OR REPLACE PROCEDURE sp_producto_eliminar(IN p_id INTEGER)
LANGUAGE plpgsql AS $$ BEGIN
 DELETE FROM PrecioProducto WHERE idproducto=p_id;
 DELETE FROM ImagenProducto WHERE idproducto=p_id;
 DELETE FROM CaracteristicaProducto WHERE idproducto=p_id;
 DELETE FROM Producto WHERE idproducto=p_id;
 IF NOT FOUND THEN RAISE EXCEPTION 'El producto ya no existe. Actualice el catálogo.'; END IF;
END $$;

CREATE OR REPLACE PROCEDURE sp_bitacora(IN p_texto TEXT, INOUT resultado REFCURSOR)
LANGUAGE plpgsql AS $$ BEGIN
 OPEN resultado FOR SELECT id,fecha,tabla,registro,operacion,usuario_bd,anterior::TEXT,nuevo::TEXT FROM Bitacora
 WHERE COALESCE(p_texto,'')='' OR concat_ws(' ',tabla,operacion,registro::TEXT,anterior::TEXT,nuevo::TEXT) ILIKE '%'||p_texto||'%'
 ORDER BY id DESC LIMIT 1000;
END $$;
COMMIT;
