\set ON_ERROR_STOP on

CREATE DATABASE datec_catalogo
    ENCODING 'UTF8'
    TEMPLATE template0;

\connect datec_catalogo

\encoding UTF8

CREATE TABLE Marca (
    idMarca SERIAL PRIMARY KEY,
    nombreMarca VARCHAR(50) NOT NULL UNIQUE CHECK (TRIM(nombreMarca) <> ''),
    descripcion TEXT
);

CREATE TABLE Categoria (
    idCategoria SERIAL PRIMARY KEY,
    nombreCategoria VARCHAR(50) NOT NULL UNIQUE CHECK (TRIM(nombreCategoria) <> ''),
	descripcion TEXT
);

CREATE TABLE TipoPresentacion (
    idTipoPresentacion SERIAL PRIMARY KEY,
    nombrePresentacion VARCHAR(30) NOT NULL UNIQUE CHECK (TRIM(nombrePresentacion) <> ''),
	abreviatura VARCHAR(10) NOT NULL CHECK (TRIM(abreviatura) <> ''),
    descripcion TEXT
);

CREATE TABLE Producto (
    idProducto SERIAL PRIMARY KEY,
    idMarca INTEGER NOT NULL
        REFERENCES Marca(idMarca)
        ON DELETE RESTRICT,
    idCategoria INTEGER NOT NULL
        REFERENCES Categoria(idCategoria)
        ON DELETE RESTRICT,
    idTipoPresentacion INTEGER NOT NULL
        REFERENCES TipoPresentacion(idTipoPresentacion)
        ON DELETE RESTRICT,
    estadoProducto VARCHAR(5) NOT NULL
        CHECK (estadoProducto IN ('Nuevo', 'Usado')),
    nombreProducto VARCHAR(150) NOT NULL
        CHECK (TRIM(nombreProducto) <> ''),
    descripcion TEXT,
    modelo VARCHAR(80)
        CHECK (modelo IS NULL OR TRIM(modelo) <> ''),
    codigoProducto VARCHAR(50) NOT NULL UNIQUE
        CHECK (TRIM(codigoProducto) <> '')
);

CREATE TABLE PrecioProducto (
    idPrecio SERIAL PRIMARY KEY,
    idProducto INTEGER NOT NULL
        REFERENCES Producto(idProducto)
        ON DELETE RESTRICT,
    tipoPrecio VARCHAR(20) NOT NULL
        CHECK (tipoPrecio IN ('Minorista', 'Mayorista')),
    monto NUMERIC(10,2) NOT NULL
        CHECK (monto BETWEEN 0.01 AND 99999999.99),
    cantidadMinima INTEGER NOT NULL
        CHECK (cantidadMinima > 0),
    UNIQUE (idProducto, tipoPrecio),
    CHECK (
	    (tipoPrecio = 'Minorista' AND cantidadMinima = 1)
	    OR
	    (tipoPrecio = 'Mayorista' AND cantidadMinima IN (2, 3))
	)
);

CREATE TABLE ImagenProducto (
    idImagen SERIAL PRIMARY KEY,

    idProducto INTEGER NOT NULL
        REFERENCES Producto(idProducto)
        ON DELETE RESTRICT,

    imagen TEXT NOT NULL
        CHECK (TRIM(imagen) <> '')
);

CREATE TABLE CaracteristicaProducto (
    idCaracteristica SERIAL PRIMARY KEY,

    idProducto INTEGER NOT NULL
        REFERENCES Producto(idProducto)
        ON DELETE RESTRICT,

    caracteristica VARCHAR(255) NOT NULL
        CHECK (TRIM(caracteristica) <> ''),
    UNIQUE (idProducto, caracteristica)
);

CREATE INDEX idx_producto_marca
    ON Producto(idMarca);

CREATE INDEX idx_producto_categoria
    ON Producto(idCategoria);

CREATE INDEX idx_producto_presentacion
    ON Producto(idTipoPresentacion);

CREATE INDEX idx_imagen_producto
    ON ImagenProducto(idProducto);

CREATE INDEX idx_producto_nombre
    ON Producto(nombreProducto);

CREATE INDEX idx_precio_monto
    ON PrecioProducto(monto);

CREATE INDEX idx_caracteristica_texto
    ON CaracteristicaProducto(caracteristica);

CREATE INDEX idx_producto_estado
    ON Producto(estadoProducto);