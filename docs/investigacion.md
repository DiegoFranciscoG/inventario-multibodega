# Investigación y fuentes

Todas las reglas de negocio del sistema salen de esta tabla. Cada regla tiene un código `R-xx` que se cita en
[modelo-datos.md](modelo-datos.md) y en el código (validaciones, restricciones SQL y tests).

Fecha de consulta de todas las fuentes: **2026-09-23**.

## 1. Fuentes consultadas

| # | Fuente (oficial / confiable) | URL | Consultada | Qué se tomó de aquí |
|---|---|---|---|---|
| 1 | IFRS Foundation — NIC 2 / IAS 2 *Inventories* (texto oficial) | https://www.ifrs.org/issued-standards/list-of-standards/ias-2-inventories/ | 2026-09-23 | Párr. 9 (menor entre costo y VNR), 10–11 (componentes del costo), 23 (identificación específica), 25 (FIFO o promedio ponderado; misma fórmula para inventarios de naturaleza similar), 26 (la ubicación geográfica no justifica otra fórmula), 27 (promedio periódico **o a medida que llega cada entrega**), 34 (el costo de lo vendido se reconoce como gasto), 36 (revelar la fórmula usada). LIFO no está permitido. |
| 2 | IFRS Foundation — NIIF para las PYMES, Módulo educativo 13 *Inventarios* (2026) | https://www.ifrs.org/content/dam/ifrs/supporting-implementation/smes/2026-modules/module-13.pdf | 2026-09-23 | Párr. 13.4 (menor entre costo y precio de venta estimado menos costos) y 13.18: FIFO o promedio ponderado; **“LIFO is not permitted by this Standard”**. |
| 3 | Superintendencia de Compañías, Valores y Seguros — Resolución 08.G.DSC.010 (20-nov-2008) | https://www.supercias.gob.ec/bd_supercias/descargas/niif/Resolucion.pdf | 2026-09-23 | Las compañías controladas en Ecuador aplican NIIF (completas o PYMES), por lo tanto NIC 2 / Sección 13 rigen la valoración del inventario. |
| 4 | GS1 — Application Identifiers (referencia oficial) | https://ref.gs1.org/ai/ | 2026-09-23 | AI (01) GTIN N2+N14 · AI (10) lote N2+X..20 (longitud variable, requiere FNC1) · AI (11) producción N2+N6 · AI (15) consumo preferente · AI (17) vencimiento N2+N6 `YYMMDD` · AI (21) serie X..20 · AI (37) cantidad N..8. |
| 5 | GS1 US — *How to calculate a check digit manually* | https://www.gs1us.org/tools/check-digit-calculator | 2026-09-23 | Algoritmo del dígito verificador para GTIN-8/12/13/14 y SSCC: multiplicar por 3 y 1 alternadamente, sumar y restar del múltiplo de 10 igual o superior (ejemplo oficial: 61414121022 → 3). |
| 6 | GS1 Ecuador — GTIN: Identificador Mundial de Artículo Comercial | https://gs1ec.org/contenido/estandares/estandares-gs1/identificar/gtin-identificador-mundial-de-articulo-comercial/ | 2026-09-23 | GTIN-13 para punto de venta, GTIN-14 para unidades logísticas (indicador + dígito verificador), GTIN-8 solo si no hay espacio. GS1 Ecuador es quien asigna los códigos. |
| 7 | GS1 — *GS1 Style Guide* (prefijo 952) | https://www.gs1.org/standards/gs1-style-guide/current-standard | 2026-09-23 | El prefijo GS1 **952** está reservado para demostraciones y ejemplos; nunca identifica artículos reales → los datos semilla usan GTIN 952…. |
| 8 | GS1 — *2D Barcodes at Retail POS Implementation Guideline* / GS1 Digital Link URI syntax | https://ref.gs1.org/guidelines/2d-in-retail/ · https://ref.gs1.org/standards/digital-link/uri-syntax/ | 2026-09-23 | Un QR puede llevar un URI GS1 Digital Link: `https://dominio/01/{GTIN}/10/{lote}?17=YYMMDD`. |
| 9 | ISO — ISO/IEC 18004:2024 *QR code bar code symbology specification* (ed. 4, 2024-08) | https://www.iso.org/standard/83389.html | 2026-09-23 | Norma vigente del símbolo QR (codificación, corrección de errores, algoritmo de decodificación). La lectura/escritura se delega a ZXing, que implementa la simbología. |
| 10 | W3C — *Media Capture and Streams* y *Secure Contexts* | https://www.w3.org/TR/mediacapture-streams/ · https://www.w3.org/TR/secure-contexts/ | 2026-09-23 | `getUserMedia` (cámara) es una capacidad sensible: el lector solo funciona en contexto seguro (HTTPS o `localhost`). |
| 11 | OMS — TRS 961, Anexo 9, §8.1.1 *Stock control systems and procedures* | https://www.who.int/docs/default-source/medicines/norms-and-standards/guidelines/distribution/trs961-annex9-modelguidanceforstoragetransport.pdf | 2026-09-23 | Requisitos mínimos de control de existencias: acceso solo a personas autorizadas, registrar todo ingreso y despacho, registrar lote y vencimiento, registrar productos próximos a vencer y vencidos, despachar en orden **EEFO/FEFO**, hacer inventarios físicos regulares, conciliar registros contra el conteo e investigar las diferencias. |
| 12 | DeHoratius, N. & Raman, A. (2008). *Inventory Record Inaccuracy: An Empirical Analysis*. Management Science 54(4), 627–641 (revisado por pares) | https://doi.org/10.1287/mnsc.1070.0789 | 2026-09-23 | El 65 % de ~370 000 registros de inventario estudiados eran inexactos; las prácticas de auditoría/conteo reducen la inexactitud → justifica el conteo cíclico con conciliación. |
| 13 | Rouwenhorst, B. et al. (2000). *Warehouse design and control: Framework and literature review*. EJOR 122(3), 515–533 (revisado por pares) | https://doi.org/10.1016/S0377-2217(99)00020-X | 2026-09-23 | Marco de referencia de bodegas: sistemas de almacenamiento compuestos por pasillos, estanterías y niveles → jerarquía bodega → pasillo → estante → nivel. |
| 14 | ASCM (APICS) — material del capítulo Wisconsin *Top 10 Inventory Mistakes* | https://wisconsin.ascm.org/images/meeting/020817/demandsolutions_top_10_inventory_mistakes.pdf | 2026-09-23 | Práctica: programar la frecuencia de conteo según clasificación ABC y aumentarla en ítems con discrepancias. (Fuente práctica; se usa junto con #11 y #12, no sola.) |
| 15 | COSO — *Internal Control – Integrated Framework* (2013), Principio 10 | https://www.coso.org/guidance-on-ic | 2026-09-23 | Punto de enfoque “Addresses Segregation of Duties”: quien registra no autoriza → quien cuenta no aprueba el ajuste. |
| 16 | SRI — Ficha técnica de comprobantes electrónicos, esquema offline | https://www.sri.gob.ec/facturacion-electronica | 2026-09-23 | Códigos oficiales de tipo de comprobante: 01 Factura, 03 Liquidación de compra, 04 Nota de crédito, 05 Nota de débito, 06 Guía de remisión, 07 Comprobante de retención → catálogo de documentos de referencia. |
| 17 | UNECE — Recomendación 20 (vía lista oficial Peppol/EN 16931, rev. 11e) | https://docs.peppol.eu/pracc/catalogue/1.0/codelist/UNECERec20/ | 2026-09-23 | Códigos de unidad: H87 (pieza), C62 (uno/unidad), KGM (kilogramo), GRM (gramo), LTR (litro), DZN (docena). |
| 18 | Asamblea Nacional — Ley Orgánica de Protección de Datos Personales (R.O. 5.º Supl. 459, 26-may-2021) | https://www.asambleanacional.gob.ec/es/multimedios-legislativos/63464-ley-organica-de-proteccion-de-datos | 2026-09-23 | Principio de minimización: de los usuarios solo se guarda correo, nombre y rol; nada de cédula ni IP; datos de demo ficticios. |
| 19 | Jakarta Persistence 3.2 — §3.5.2 *Entity Versions and Optimistic Locking*, §3.5.5 `OptimisticLockException` | https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2 | 2026-09-23 | `@Version` detecta actualizaciones concurrentes y lanza `OptimisticLockException` en lugar de sobrescribir en silencio. |
| 20 | PostgreSQL 17 — `CREATE TABLE` | https://www.postgresql.org/docs/17/sql-createtable.html | 2026-09-23 | `CHECK` rechaza filas inválidas (p. ej. stock negativo) y `UNIQUE NULLS NOT DISTINCT` trata los NULL como iguales (stock sin lote). |
| 21 | OWASP Top 10:2025 | https://top10.owasp.org/2025 | 2026-09-23 | A01 Broken Access Control … A10 Mishandling of Exceptional Conditions → controles de la sección de seguridad. |
| 22 | OWASP API Security Top 10:2023 | https://api-security.owasp.org/editions/2023/en/0x11-t10 | 2026-09-23 | API1 BOLA (acceso por bodega), API2 autenticación, API4 consumo de recursos (paginación y límites de exportación), API5 autorización por función (roles). |
| 23 | Spring — calendario de soporte y requisitos de Spring Boot | https://spring.io/projects/spring-boot · https://docs.spring.io/spring-boot/system-requirements.html · https://eosl.date/eol/product/spring-boot/ | 2026-09-23 | **Spring Boot 3.5 terminó su soporte OSS el 30-jun-2026** (última 3.5.16). La versión estable es **4.1.1** (soporte OSS hasta 31-jul-2027), requiere Java 17+ y es compatible hasta Java 26. |
| 24 | npm registry — `@angular/core` 22.1.7, `@angular/cli` 22.1.8, `@zxing/browser` 0.2.1, `@zxing/library` 0.23.0 | https://www.npmjs.com/package/@angular/core · https://www.npmjs.com/package/@zxing/browser | 2026-09-23 | Angular 22 exige Node `^22.22.3 || ^24.15.0 || >=26` → se construye con Node 24 LTS. |
| 25 | Maven Central — Apache POI 5.5.1, springdoc-openapi 3.1.1, Testcontainers 2.0.5, Bucket4j 8.20.0, JaCoCo 0.8.15 | https://repo1.maven.org/maven2/ | 2026-09-23 | Últimas versiones estables de las dependencias del backend. |
| 26 | Render — *Free instance types* | https://render.com/docs/free | 2026-09-23 | El servicio gratis se duerme tras 15 min sin tráfico (~1 min en despertar), 750 h/mes por workspace; Postgres gratis expira a los 30 días → la BD va en Neon. |
| 27 | Neon — límites del plan gratis y versiones de Postgres | https://neon.com/pricing · https://neon.com/docs/postgresql/postgres-version-policy | 2026-09-23 | 0,5 GB y 100 CU-h por proyecto; soporta Postgres 14–18 → usamos 17 (igual que en local y en tests). |
| 28 | Vercel — *Hobby plan* | https://vercel.com/docs/plans/hobby | 2026-09-23 | Gratis, solo uso personal/no comercial, 100 GB de transferencia al mes → adecuado para un portafolio. |
| 29 | Docker Hub — imágenes oficiales | https://hub.docker.com/_/postgres · https://hub.docker.com/_/eclipse-temurin · https://hub.docker.com/_/node | 2026-09-23 | Etiquetas fijas: `postgres:17.11-alpine3.24`, `eclipse-temurin:25.0.4_7-jdk/jre-alpine-3.24`, `node:24.21.0-alpine3.24`. |

## 2. Reglas de negocio derivadas

| Código | Regla | Fuente |
|---|---|---|
| R-01 | El inventario se valora con **costo promedio ponderado**. FIFO sería igual de válido; **LIFO está prohibido** y el sistema no lo ofrece. | #1 párr. 25 · #2 13.18 · #3 |
| R-02 | El promedio se recalcula **en cada ingreso** (promedio móvil/perpetuo): `nuevo_promedio = (valor_actual + cant_ingreso × costo_ingreso) / (cant_actual + cant_ingreso)`. | #1 párr. 27 |
| R-03 | Una sola fórmula y **un solo costo promedio por producto para toda la empresa** (área de valoración = empresa). Las transferencias entre bodegas propias no cambian el costo, solo la ubicación. | #1 párr. 25–26 |
| R-04 | Los egresos y ajustes negativos salen al **costo promedio vigente**; ese valor es el costo reconocido. | #1 párr. 34 |
| R-05 | El costo unitario de un ingreso es el costo de adquisición completo (precio + aranceles + transporte) que registra el usuario; el sistema no prorratea gastos. | #1 párr. 10–11 (el prorrateo queda como SUPUESTO/roadmap) |
| R-06 | Todo movimiento queda en el **kárdex**: fecha, documento, tipo, cantidad, costo unitario, costo total y saldos (cantidad, valor y promedio) después del movimiento. El kárdex es **inmutable**: las correcciones se hacen con nuevos movimientos. | #1 párr. 36 · #11 |
| R-07 | **Nunca stock negativo**: una salida mayor al disponible en esa bodega/ubicación/lote se rechaza. Se refuerza con `CHECK (quantity >= 0)` en la BD. | #11 · #20 |
| R-08 | La transferencia entre bodegas es **una sola transacción** (salida + entrada); si algo falla no cambia nada. | #11 (registrar todo despacho y recepción) |
| R-09 | El **kárdex cuadra con el stock**: para cada producto, Σ entradas − Σ salidas = Σ stock por ubicación = saldo del último renglón del kárdex = cantidad de la tabla de costo. Se expone como reporte de conciliación y se prueba en los tests. | #11 (conciliar registros) · #12 |
| R-10 | Concurrencia segura: `stock` y `product_cost` llevan `@Version`; si dos transacciones tocan el mismo registro, la segunda falla con `OptimisticLockException` y se reintenta (máx. 3) desde cero. | #19 |
| R-11 | GTIN válido = 8, 12, 13 o 14 dígitos con dígito verificador GS1 correcto. Se guarda **normalizado a 14 dígitos** (ceros a la izquierda), así un GTIN-13 y su forma de 14 dígitos son el mismo producto. | #4 · #5 · #6 |
| R-12 | Las unidades logísticas (caja, paquete) tienen factor de conversión a la unidad base y pueden tener su propio **GTIN-14**; al escanearlo se identifica el producto y la unidad. | #6 |
| R-13 | Lote = texto de hasta **20** caracteres (AI 10); vencimiento = fecha (AI 17, `YYMMDD`). Los productos con control de lote exigen lote en cada ingreso. | #4 |
| R-14 | El lector acepta: GTIN simple, cadena GS1 (AIs 01/10/17/11/21/37 con separador FNC1/GS o en formato `(01)…`), URI **GS1 Digital Link**, QR interno de ubicación (`LOC:{bodega}:{ubicación}`) y SKU. | #4 · #8 · #9 |
| R-15 | Los datos de demostración usan GTIN con prefijo **952**. | #7 |
| R-16 | La cámara solo se usa en **HTTPS** (Vercel) o `localhost`. | #10 |
| R-17 | Ubicaciones jerárquicas **bodega → pasillo → estante → nivel**; el código de ubicación es único dentro de su bodega (`P01-E02-N3`). | #13 |
| R-18 | Despacho **FEFO**: si el egreso no indica lote/ubicación, el sistema asigna primero lo que vence antes. Los lotes vencidos no se despachan (solo se dan de baja con ajuste). | #11 |
| R-19 | Alertas: stock por bodega por debajo del mínimo configurado, y lotes con existencia que vencen dentro de N días (30 por defecto) o ya vencidos. | #11 |
| R-20 | **Conteo cíclico**: se genera por bodega (filtros opcionales por pasillo y clase ABC); el contador ve un conteo **ciego** (sin la cantidad del sistema); la diferencia = contado − sistema al momento de contar; se valoriza al costo promedio. | #11 · #12 · #14 |
| R-21 | El ajuste por conteo requiere **aprobación de un SUPERVISOR distinto de quien contó** (segregación de funciones); al aprobar se genera un movimiento de AJUSTE enlazado al conteo. Todo queda en la **bitácora de auditoría** (inmutable). | #15 · #11 |
| R-22 | Documento de referencia obligatorio en cada movimiento; tipos SRI (01, 03, 04, 05, 06) más tipos internos (orden de compra, pedido, orden de transferencia, acta de ajuste, conteo cíclico, saldo inicial). | #16 |
| R-23 | Unidades de medida con códigos UNECE Rec. 20. | #17 |
| R-24 | No se permiten movimientos con fecha anterior al último movimiento del producto (preserva la cronología del kárdex). | #1 párr. 27 (el promedio depende del orden) — SUPUESTO de diseño |
| R-25 | Usuarios: solo correo, nombre y rol; un OPERADOR solo opera en las bodegas que tiene asignadas (protección BOLA). | #18 · #22 |

## 3. Supuestos (no verificados o decisiones de diseño) — **revisar**

- **S-01 · Spring Boot 4.1.1 en lugar de 3.x.** El enunciado pide Spring Boot 3, pero la rama 3.5 quedó sin parches OSS desde el 30-jun-2026 (fuente #23). Usar una versión sin parches contradice OWASP A03:2025 (cadena de suministro). Se usa 4.1.1, la estable vigente.
- **S-02 · Java 25 (LTS).** Spring Boot 4.1 soporta 17–26; se compila con `release 25` y las imágenes usan Temurin 25.
- **S-03 · Códigos de unidad MLT (mililitro) y XBX (caja, Rec. 21 con prefijo X).** La lista oficial consultada no los mostró completos (la web de UNECE bloquea descargas automáticas). Se usan como supuesto.
- **S-04 · AI (17) con día `00`.** GS1 indica que `00` significa “día no especificado”; el sistema lo interpreta como **último día del mes** (criterio conservador).
- **S-05 · Valor neto realizable (NIC 2 párr. 9).** El MVP no calcula deterioro a VNR; queda en el roadmap.
- **S-06 · Prorrateo de costos de importación.** El usuario ingresa el costo unitario total; no hay prorrateo automático.
- **S-07 · Frecuencias ABC.** El sistema permite filtrar conteos por clase ABC, pero no programa frecuencias automáticas (la fuente #14 es práctica, no norma).
- **S-08 · Contraseña de demo.** Los usuarios de demo usan la contraseña de la variable `DEMO_PASSWORD`; en la demo pública se publica en el README porque los datos son ficticios y los usuarios no son administradores.
