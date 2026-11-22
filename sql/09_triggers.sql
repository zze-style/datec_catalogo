\set ON_ERROR_STOP on
\encoding UTF8
-- =====================================================================================
-- 09_triggers.sql
-- Triggers de reglas de negocio del catálogo DATEC.
-- Ejecutar como postgres (dueño de las tablas) conectado a datec_catalogo, después de 04.
-- Es seguro repetirlo: cada trigger se elimina y se vuelve a crear.
--
-- Un trigger es una función que PostgreSQL ejecuta solo, sin que el aplicativo la llame,
-- cada vez que ocurre un INSERT, UPDATE o DELETE en una tabla:
--   * BEFORE: se ejecuta antes de guardar la fila; puede corregirla o rechazarla.
--   * AFTER:  se ejecuta después; sirve para registrar (bitácora) o comprobar el resultado.
--   * CONSTRAINT ... DEFERRABLE INITIALLY DEFERRED: la comprobación espera al final de la
--     transacción, cuando ya se guardaron todos los cambios de la operación.
-- Así las reglas se cumplen aunque los datos se modifiquen desde psql u otro programa.
--
-- Triggers del sistema:
--   tr_auditoria                 (04)  AFTER  I/U/D  siete tablas   Bitácora de cambios
--   tr_producto_normalizar       (09)  BEFORE I/U    Producto       Limpia textos y código
--   tr_caracteristica_normalizar (09)  BEFORE I/U    Característica Limpia el texto
--   tr_imagen_validar            (09)  BEFORE I/U    ImagenProducto Solo enlaces web, sin repetir
--   tr_precio_coherente          (09)  AFTER  I/U/D  PrecioProducto Mayorista < minorista
--   tr_clasificacion_en_uso      (09)  BEFORE D      Marca, Categoría, Presentación
-- =====================================================================================
SET client_min_messages = warning;
BEGIN;

-- -------------------------------------------------------------------------------------
-- 1. Producto: normaliza los textos antes de guardar.
--    El código se guarda en mayúsculas para que «tin-001» y «TIN-001» no puedan coexistir.
--    Los textos opcionales vacíos se guardan como NULL.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_producto_normalizar() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
 NEW.codigoproducto := upper(btrim(NEW.codigoproducto));
 NEW.nombreproducto := regexp_replace(btrim(NEW.nombreproducto), '\s+', ' ', 'g');
 NEW.modelo         := NULLIF(btrim(NEW.modelo), '');
 NEW.descripcion    := NULLIF(btrim(NEW.descripcion), '');
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS tr_producto_normalizar ON Producto;
CREATE TRIGGER tr_producto_normalizar
 BEFORE INSERT OR UPDATE ON Producto
 FOR EACH ROW EXECUTE FUNCTION fn_producto_normalizar();

-- -------------------------------------------------------------------------------------
-- 2. CaracteristicaProducto: quita espacios sobrantes para que «Color:  negro» y
--    «Color: negro» se consideren la misma característica (UNIQUE idProducto+texto).
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_caracteristica_normalizar() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
 NEW.caracteristica := regexp_replace(btrim(NEW.caracteristica), '\s+', ' ', 'g');
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS tr_caracteristica_normalizar ON CaracteristicaProducto;
CREATE TRIGGER tr_caracteristica_normalizar
 BEFORE INSERT OR UPDATE ON CaracteristicaProducto
 FOR EACH ROW EXECUTE FUNCTION fn_caracteristica_normalizar();

-- -------------------------------------------------------------------------------------
-- 3. ImagenProducto: la base guarda solo enlaces (URL), nunca archivos ni rutas locales
--    como C:\fotos\... que no funcionarían en otro equipo. Además, un producto no puede
--    tener dos veces el mismo enlace.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_imagen_validar() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
 NEW.imagen := btrim(NEW.imagen);
 IF NEW.imagen !~* '^https?://[^[:space:]]+$' OR length(NEW.imagen) > 2048 THEN
  RAISE EXCEPTION 'La imagen debe ser un enlace web que empiece con http:// o https://.';
 END IF;
 IF EXISTS (SELECT 1 FROM ImagenProducto
            WHERE idproducto = NEW.idproducto AND imagen = NEW.imagen
              AND idimagen IS DISTINCT FROM NEW.idimagen) THEN
  RAISE EXCEPTION 'El producto ya tiene registrada esa imagen.';
 END IF;
 RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS tr_imagen_validar ON ImagenProducto;
CREATE TRIGGER tr_imagen_validar
 BEFORE INSERT OR UPDATE ON ImagenProducto
 FOR EACH ROW EXECUTE FUNCTION fn_imagen_validar();

-- -------------------------------------------------------------------------------------
-- 4. PrecioProducto: coherencia entre tarifas del mismo producto.
--    * El precio mayorista debe ser menor que el minorista (si no, no es un descuento).
--    * No puede existir tarifa mayorista sin tarifa minorista.
--    Es un trigger diferido: se comprueba al final de la transacción. Así, al bajar ambos
--    precios en una misma operación (por ejemplo 100/90 -> 80/70), no se rechaza el paso
--    intermedio. Si el producto completo se eliminó, no hay nada que comprobar.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_precio_coherente() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
 v_id        INTEGER := CASE WHEN TG_OP = 'DELETE' THEN OLD.idproducto ELSE NEW.idproducto END;
 v_minorista NUMERIC;
 v_mayorista NUMERIC;
BEGIN
 IF NOT EXISTS (SELECT 1 FROM Producto WHERE idproducto = v_id) THEN
  RETURN NULL;
 END IF;
 SELECT max(monto) FILTER (WHERE tipoprecio = 'Minorista'),
        max(monto) FILTER (WHERE tipoprecio = 'Mayorista')
   INTO v_minorista, v_mayorista
   FROM PrecioProducto WHERE idproducto = v_id;
 IF v_mayorista IS NOT NULL AND v_minorista IS NULL THEN
  RAISE EXCEPTION 'Un producto con precio mayorista debe tener también precio minorista.';
 END IF;
 IF v_mayorista IS NOT NULL AND v_mayorista >= v_minorista THEN
  RAISE EXCEPTION 'El precio mayorista (S/ %) debe ser menor que el minorista (S/ %).',
   v_mayorista, v_minorista;
 END IF;
 RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS tr_precio_coherente ON PrecioProducto;
CREATE CONSTRAINT TRIGGER tr_precio_coherente
 AFTER INSERT OR UPDATE OR DELETE ON PrecioProducto
 DEFERRABLE INITIALLY DEFERRED
 FOR EACH ROW EXECUTE FUNCTION fn_precio_coherente();

-- -------------------------------------------------------------------------------------
-- 5. Marca, Categoría y TipoPresentacion: no se eliminan si algún producto las usa.
--    La clave foránea (ON DELETE RESTRICT) ya lo impide; el trigger da un mensaje claro
--    que indica qué clasificación es y cuántos productos la usan.
--    TG_ARGV[0] es la columna de Producto que referencia a la tabla.
-- -------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_clasificacion_en_uso() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
 v_columna TEXT := TG_ARGV[0];
 v_id      INTEGER := (to_jsonb(OLD) ->> v_columna)::INTEGER;
 v_nombre  TEXT := to_jsonb(OLD) ->> TG_ARGV[1];
 v_total   INTEGER;
BEGIN
 EXECUTE format('SELECT count(*) FROM Producto WHERE %I = $1', v_columna)
    INTO v_total USING v_id;
 IF v_total > 0 THEN
  RAISE EXCEPTION 'No se puede eliminar «%»: la usan % producto(s). Cámbielos de clasificación primero.',
   v_nombre, v_total;
 END IF;
 RETURN OLD;
END $$;

DROP TRIGGER IF EXISTS tr_clasificacion_en_uso ON Marca;
CREATE TRIGGER tr_clasificacion_en_uso
 BEFORE DELETE ON Marca
 FOR EACH ROW EXECUTE FUNCTION fn_clasificacion_en_uso('idmarca', 'nombremarca');

DROP TRIGGER IF EXISTS tr_clasificacion_en_uso ON Categoria;
CREATE TRIGGER tr_clasificacion_en_uso
 BEFORE DELETE ON Categoria
 FOR EACH ROW EXECUTE FUNCTION fn_clasificacion_en_uso('idcategoria', 'nombrecategoria');

DROP TRIGGER IF EXISTS tr_clasificacion_en_uso ON TipoPresentacion;
CREATE TRIGGER tr_clasificacion_en_uso
 BEFORE DELETE ON TipoPresentacion
 FOR EACH ROW EXECUTE FUNCTION fn_clasificacion_en_uso('idtipopresentacion', 'nombrepresentacion');

COMMIT;

-- Lista de triggers instalados.
SELECT event_object_table AS tabla, trigger_name AS trigger, action_timing AS momento,
       string_agg(event_manipulation, ', ' ORDER BY event_manipulation) AS eventos
FROM information_schema.triggers
WHERE trigger_schema = 'public'
GROUP BY 1, 2, 3
ORDER BY 1, 2;
