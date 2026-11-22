\set ON_ERROR_STOP on
\connect datec_catalogo
\encoding UTF8

-- 1. Consultar la lista completa del catálogo con sus datos principales
SELECT p.codigoProducto,
       p.nombreProducto,
       p.modelo,
       m.nombreMarca,
       c.nombreCategoria,
       t.nombrePresentacion,
       p.estadoProducto
FROM Producto p
INNER JOIN Marca m ON p.idMarca = m.idMarca
INNER JOIN Categoria c ON p.idCategoria = c.idCategoria
INNER JOIN TipoPresentacion t ON p.idTipoPresentacion = t.idTipoPresentacion
ORDER BY c.nombreCategoria, p.nombreProducto, p.codigoProducto;

-- 2. Buscar un producto por nombre, modelo o código (Búsqueda comercial)
SELECT p.codigoProducto,
       p.nombreProducto,
       p.modelo,
       m.nombreMarca,
       p.estadoProducto
FROM Producto p
INNER JOIN Marca m ON p.idMarca = m.idMarca
WHERE p.nombreProducto ILIKE '%mouse%'
   OR p.modelo ILIKE '%mouse%'
   OR p.codigoProducto ILIKE '%mouse%'
ORDER BY p.nombreProducto, p.codigoProducto;

-- 3. Consulta de precios (minorista y mayorista) de un producto específico

SELECT p.codigoProducto,
       p.nombreProducto,
       t.nombrePresentacion,
       pr.tipoPrecio,
       pr.monto AS precio_soles,
       pr.cantidadMinima
FROM Producto p
INNER JOIN TipoPresentacion t ON p.idTipoPresentacion = t.idTipoPresentacion
INNER JOIN PrecioProducto pr ON p.idProducto = pr.idProducto
WHERE p.codigoProducto = 'PER-002'
ORDER BY pr.cantidadMinima, pr.tipoPrecio;

-- 4. Filtrar productos por Categoría y Marca

SELECT p.codigoProducto,
       p.nombreProducto,
       p.modelo,
       c.nombreCategoria,
       m.nombreMarca,
       p.estadoProducto
FROM Producto p
INNER JOIN Categoria c ON p.idCategoria = c.idCategoria
INNER JOIN Marca m ON p.idMarca = m.idMarca
WHERE c.nombreCategoria = 'Almacenamiento'
  AND m.nombreMarca = 'Kingston'
ORDER BY p.nombreProducto, p.codigoProducto;

-- 5. Consultar las especificaciones / características técnicas de un producto

SELECT p.codigoProducto,
       p.nombreProducto,
       cp.caracteristica
FROM Producto p
INNER JOIN CaracteristicaProducto cp ON p.idProducto = cp.idProducto
WHERE p.codigoProducto = 'PER-002'
ORDER BY cp.idCaracteristica;

-- 6. Filtrar artículos por estado (Usado o Nuevo)

SELECT p.codigoProducto,
       p.nombreProducto,
       p.modelo,
       m.nombreMarca,
       c.nombreCategoria,
       p.estadoProducto
FROM Producto p
INNER JOIN Marca m ON p.idMarca = m.idMarca
INNER JOIN Categoria c ON p.idCategoria = c.idCategoria
WHERE p.estadoProducto = 'Usado'
ORDER BY p.nombreProducto, p.codigoProducto;

-- 7. Consultar catálogo con precio minorista para atención al cliente

SELECT p.codigoProducto,
       p.nombreProducto,
       m.nombreMarca,
       t.nombrePresentacion,
       p.estadoProducto,
       pr.monto AS precio_minorista_soles
FROM Producto p
INNER JOIN Marca m ON p.idMarca = m.idMarca
INNER JOIN TipoPresentacion t ON p.idTipoPresentacion = t.idTipoPresentacion
LEFT JOIN PrecioProducto pr
    ON p.idProducto = pr.idProducto
   AND pr.tipoPrecio = 'Minorista'
ORDER BY p.nombreProducto, p.codigoProducto;

-- 8. Listar productos con sus condiciones de precio mayorista

SELECT p.codigoProducto,
       p.nombreProducto,
       m.nombreMarca,
       t.nombrePresentacion,
       pr.monto AS precio_mayorista_soles,
       pr.cantidadMinima AS unidades_minimas
FROM Producto p
INNER JOIN Marca m ON p.idMarca = m.idMarca
INNER JOIN TipoPresentacion t ON p.idTipoPresentacion = t.idTipoPresentacion
INNER JOIN PrecioProducto pr ON p.idProducto = pr.idProducto
WHERE pr.tipoPrecio = 'Mayorista'
ORDER BY pr.cantidadMinima, p.nombreProducto, p.codigoProducto;

-- 9. Verificar productos que tienen imágenes cargadas en el sistema

SELECT p.codigoProducto,
       p.nombreProducto,
       COUNT(i.idImagen) AS cantidad_imagenes
FROM Producto p
INNER JOIN ImagenProducto i ON p.idProducto = i.idProducto
GROUP BY p.idProducto, p.codigoProducto, p.nombreProducto
ORDER BY p.nombreProducto, p.codigoProducto;

-- 10. Reporte total de productos registrados por Categoría

SELECT c.nombreCategoria,
       COUNT(p.idProducto) AS total_productos
FROM Categoria c
LEFT JOIN Producto p ON c.idCategoria = p.idCategoria
GROUP BY c.idCategoria, c.nombreCategoria
ORDER BY total_productos DESC, c.nombreCategoria;

