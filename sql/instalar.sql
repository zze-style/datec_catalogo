\set ON_ERROR_STOP on
-- Solo instalación NUEVA. No elimina ni reemplaza bases existentes.
-- Ejecutar con un superusuario (postgres) desde SQL Shell (psql).
\ir 01_creacion_bd.sql
BEGIN;
\ir 02_insercion_datos.sql
COMMIT;
\ir 04_funcionalidad.sql
\ir 07_roles_reportes.sql
\ir 09_triggers.sql
