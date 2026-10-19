# Plan: Simulador de Gestión de Procesos

## Resumen del PDF

El laboratorio pide **dos aplicaciones**:

### Aplicación 1: Backend Java (Escritorio/API REST)
Genera y gestiona catálogos de procesos del sistema operativo:
- Obtener N procesos del SO (por CPU o memoria)
- Para cada proceso: PID, Nombre, Usuario, Descripción, Prioridad (0=Expulsivo, 1=No Expulsivo)
- **Catálogo**: número consecutivo + nombre descriptivo para agrupar procesos
- **Actividad**: cada proceso crea un archivo con su nombre, escribe la descripción carácter por carácter (cada carácter = 1 quantum)
- **Web Service REST (XML)**: exponer procesos y datos del algoritmo
- Los catálogos se almacenan en **base de datos**

### Aplicación 2: Web - Simulador Round Robin
- Consume el servicio REST de la App 1
- **Modelo de estados**: Listo → Ejecución → Terminado
- **Algoritmo**: Round Robin
- **Estructuras dinámicas** para cada estado
- **TH**: unidad en milisegundos definida por el usuario
- **TR (ráfaga)** = TH × (cantidad de caracteres de la descripción)
- **Tiempo de llegada**: si LISTO vacío → 0, sino → tiempo_llegada_último + 1
- **Tiempo de finalización** = Quantum × #Ejecuciones
- Cada proceso tiene su propio **Thread**
- Simulación puede ser **interrumpida y reiniciada**
- **Reportes**: tabla con P, T.L, R, P.R, T.R, T.F
- **Gráfico de comportamiento** (Gantt chart)
- Liberación CPU: (a) culminó actividad, (b) es expulsivo
- Ejecución→Listo solo si es expulsivo
- Ejecución→Terminado si actividad completada

## Arquitectura

```
┌──────────────────────────┐      REST/XML       ┌──────────────────────────┐
│  App 1: Java Backend     │ ◄──────────────────► │  App 2: Web Frontend     │
│  (Spring Boot)           │                      │  (HTML/CSS/JS)           │
│                          │                      │                          │
│  - Captura procesos SO   │                      │  - Simulador Round Robin │
│  - CRUD Catálogos        │                      │  - Modelo de Estados     │
│  - BD H2/SQLite          │                      │  - Gráfico Gantt         │
│  - REST XML endpoints    │                      │  - Reportes              │
│  - Actividad (archivos)  │                      │  - Threads (Workers)     │
└──────────────────────────┘                      └──────────────────────────┘
```

## Stack Tecnológico
- **Backend**: Java 17+ con Spring Boot, H2 (base de datos embebida), REST XML
- **Frontend**: HTML5 + CSS3 + JavaScript vanilla (diseño premium)

## Plan de Implementación

### Fase 1: Backend Java (Spring Boot)
1. Inicializar proyecto Spring Boot con Maven
2. Modelo de datos: Catalogo, Proceso
3. Servicio para capturar procesos del SO (ProcessHandle API de Java)
4. Base de datos H2 con JPA
5. REST Controllers que producen XML
6. Lógica de actividad (crear archivos)

### Fase 2: Frontend Web
1. Página principal con diseño premium
2. Consumir REST API
3. Simulador Round Robin con Web Workers
4. Visualización de modelo de estados
5. Gráfico de Gantt interactivo
6. Reportes con tablas
7. Controles de pausa/reanudar
