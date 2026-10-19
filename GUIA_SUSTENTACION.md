# 🎓 GUÍA DE SUSTENTACIÓN — Simulador de Procesos Round Robin
### Universidad de Córdoba · Sistemas Operativos 2026-II
### Entrega: 20/10/2026 · 1:30 p.m.

---

## 📋 DISTRIBUCIÓN DE ROLES (4 personas)

| # | Persona | Tema | Tiempo estimado |
|---|---------|------|-----------------|
| **P1** | Arquitectura & Backend Java | Estructura del proyecto, Spring Boot, modelos, base de datos | ~5 min |
| **P2** | Captura de procesos del SO | `SistemaOperativoService`, PowerShell, `ProcessHandle`, prioridades | ~5 min |
| **P3** | Servicio REST XML & GUI Escritorio | `CatalogoController`, endpoints XML, `AppEscritorioGUI` (Swing) | ~5 min |
| **P4** | Simulador Web Round Robin | `app.js`, algoritmo Round Robin, hilos Web Worker, reportes y gráficas | ~5 min |

---

## 👤 PERSONA 1 — Arquitectura General & Backend

### ¿Qué explica?
La estructura completa del proyecto, cómo arranca la aplicación y cómo se conectan las capas.

### Puntos a cubrir

**1. Tecnologías usadas**
- **Java 21 + Spring Boot 4.1** como backend único que sirve tanto la app de escritorio como el servicio REST
- **H2 Database** (en memoria) como base de datos relacional
- **FlatLaf** para el estilo moderno de la GUI Swing
- **HTML + CSS + JavaScript puro** para el simulador web (frontend)
- **Chart.js** para las gráficas del reporte
- **Jackson XML** para la serialización en XML del servicio REST

**2. Estructura del proyecto**
```
simulador-procesos/
├── src/main/java/com/unicordoba/simulador/
│   ├── SimuladorProcesosApplication.java   ← Punto de entrada (Boot + Swing)
│   ├── model/
│   │   ├── Catalogo.java                   ← Entidad BD: agrupa procesos
│   │   └── Proceso.java                    ← Entidad BD: un proceso del SO
│   ├── repository/
│   │   ├── CatalogoRepository.java         ← CRUD en BD para catálogos
│   │   └── ProcesoRepository.java          ← CRUD en BD para procesos
│   ├── service/
│   │   ├── SistemaOperativoService.java    ← Lee procesos reales del SO
│   │   └── CatalogoService.java            ← Lógica de negocio principal
│   ├── controller/
│   │   └── CatalogoController.java         ← API REST (endpoints)
│   ├── gui/
│   │   └── AppEscritorioGUI.java           ← Interfaz Java Swing
│   └── config/
│       └── WebConfig.java                  ← CORS y configuración web
└── src/main/resources/static/
    ├── index.html                          ← Simulador web (App 2)
    ├── styles.css                          ← Diseño de la app web
    └── app.js                              ← Lógica del simulador RR
```

**3. Cómo arranca la aplicación**

```java
// SimuladorProcesosApplication.java — LÍNEAS 17-40
public static void main(String[] args) {
    new SpringApplicationBuilder(SimuladorProcesosApplication.class)
            .headless(false)   // ← Permite GUI Swing
            .run(args);
}

@Bean
public CommandLineRunner launchDesktopGui(...) {
    return args -> {
        EventQueue.invokeLater(() -> {
            FlatLightLaf.setup();  // ← Tema moderno para Swing
            AppEscritorioGUI gui = new AppEscritorioGUI(...);
            gui.setVisible(true);
        });
    };
}
```

> 💡 **Punto clave**: Una sola ejecución (`mvn spring-boot:run` o el `.bat`) lanza simultáneamente el servidor web (puerto 8080) Y la ventana de escritorio Swing. Son la misma aplicación.

**4. Modelos de datos (Entidades JPA)**

```java
// Catalogo.java — Tabla "catalogos" en H2
@Entity
public class Catalogo {
    private Long id;
    private Integer numeroCatalogo;   // Número consecutivo
    private String nombreCatalogo;    // Nombre descriptivo del usuario
    private String criterioSeleccion; // "CPU" o "MEMORIA"
    @OneToMany                        // Un catálogo tiene muchos procesos
    private List<Proceso> procesos;
}

// Proceso.java — Tabla "procesos" en H2
@Entity
public class Proceso {
    private Long pid;
    private String nombre;
    private String usuario;
    private String descripcion;
    private Integer prioridad; // 0 = Expulsivo, 1 = No Expulsivo (SO)
    private Double usoCpu;
    private Double usoMemoria;
}
```

---

## 👤 PERSONA 2 — Captura de Procesos del Sistema Operativo

### ¿Qué explica?
Cómo se obtienen los procesos reales del computador y cómo se clasifican.

### Puntos a cubrir

**1. El servicio `SistemaOperativoService.java`**

Este servicio usa **PowerShell** para leer los procesos reales del sistema operativo de Windows:

```java
// SistemaOperativoService.java — LÍNEAS 31-41
String psScript = "& { Get-Process | " +
    "Where-Object { $_.Id -gt 0 -and $_.ProcessName -ne 'Idle' } | " +
    "Sort-Object " + sortProperty + " -Descending | " +
    "Select-Object -First " + (cantidad + 5) + " ..."
    " | ConvertTo-Csv -NoTypeInformation }";

ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", psScript);
```

> 💡 **Punto clave**: Java ejecuta un comando PowerShell (`Get-Process`) que devuelve los procesos en formato CSV, luego Java los parsea línea por línea.

**2. Criterios de ordenamiento**
- **Por CPU** → `Sort-Object CPU -Descending` → procesos que más CPU consumen primero
- **Por MEMORIA** → `Sort-Object WorkingSet64 -Descending` → mayor uso de RAM primero

**3. Clasificación de prioridad (EXPULSIVO vs NO EXPULSIVO)**

```java
// SistemaOperativoService.java — LÍNEAS 63-70
boolean esSO = "SYSTEM".equalsIgnoreCase(usuario)
        || usuario.toUpperCase().contains("LOCAL SERVICE")
        || nombre.matches("(?i).*(svchost|lsass|csrss|winlogon|system|smss|...).*");

int prioridad = esSO ? 1 : 0;
// prioridad 1 = No Expulsivo (proceso del sistema operativo)
// prioridad 0 = Expulsivo (proceso de usuario)
```

> 💡 **Regla**: Si el proceso pertenece al sistema operativo (usuario SYSTEM, nombres como `svchost`, `lsass`, `csrss`, etc.), entonces es **No Expulsivo (prioridad = 1)**. Si es de usuario, es **Expulsivo (prioridad = 0)**.

**4. Fallback con Java ProcessHandle**

```java
// SistemaOperativoService.java — LÍNEAS 121-148
// Si PowerShell falla, se usa la API nativa de Java 21
private List<Proceso> obtenerProcesosJava(int cantidad) {
    return ProcessHandle.allProcesses()
        .filter(ph -> ph.info().command().isPresent())
        .limit(cantidad)
        .map(ph -> { ... })
        .collect(Collectors.toList());
}
```

**5. Limitación de descripción a 60 caracteres**

```java
// LÍNEAS 77-80
if (descripcion.length() > 60) {
    descripcion = descripcion.substring(0, 60);
}
```

> 💡 **¿Por qué?** La ráfaga se calcula como `TR = TH × caracteres`. Si la descripción tiene 500 caracteres, la simulación tardaría eternamente. Se limita a 60 para mantener ráfagas razonables.

---

## 👤 PERSONA 3 — Servicio REST XML & Interfaz Gráfica de Escritorio

### ¿Qué explica?
El puente REST entre las dos aplicaciones y la GUI Swing de la App 1.

### Puntos a cubrir

**1. Endpoints REST del `CatalogoController.java`**

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/catalogos/xml` | **Todos los catálogos en XML** (consumido por la App 2) |
| `GET` | `/api/catalogos/{id}/xml` | Un catálogo específico en XML |
| `GET` | `/api/catalogos` | Todos los catálogos en JSON |
| `GET` | `/api/catalogos/{id}/procesos` | Procesos de un catálogo |
| `POST` | `/api/catalogos` | Crear catálogo nuevo |
| `DELETE` | `/api/catalogos/{id}` | Eliminar catálogo |
| `POST` | `/api/catalogos/{id}/ejecutar-actividad?th=N` | **Lanza hilos Java** por proceso |

**2. Generación del XML (sin librerías externas)**

```java
// CatalogoService.java — LÍNEAS 164-198
public String generarCatalogosXml() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<catalogos total=\"").append(catalogos.size()).append("\">\n");
    // ... construye el XML elemento por elemento
    for (Proceso p : c.getProcesos()) {
        xml.append("      <proceso>\n");
        xml.append("        <pid>").append(p.getPid()).append("</pid>\n");
        xml.append("        <descripcion>").append(escapeXml(p.getDescripcion())).append("</descripcion>\n");
        // ...
    }
}
```

> 💡 **Punto clave**: El XML se construye manualmente con StringBuilder para tener control total sobre el formato. El método `escapeXml()` protege caracteres especiales (`&`, `<`, `>`, `"`, `'`).

**3. Interfaz Gráfica de Escritorio (`AppEscritorioGUI.java`)**

Tiene **3 pasos** bien marcados en la GUI:

```
PASO 1: Captura procesos del SO
  ├── Selecciona cantidad (N) y criterio (CPU / MEMORIA)
  ├── Botón "Capturar" → llama a soService.obtenerProcesos()
  └── Muestra la tabla de procesos en pantalla

PASO 2: Catalogar y guardar en BD
  ├── Usuario escribe N° de catálogo y nombre descriptivo
  ├── Botón "Guardar Catálogo" → llama a catalogoService.guardarCatalogoConProcesos()
  └── La web ya puede usar el catálogo inmediatamente vía REST

PASO 3: Actividad de Procesos (Hilos)
  ├── Usuario establece TH (milisegundos por carácter)
  ├── Botón "Ejecutar Hilos" → lanza un Thread por proceso
  └── Cada Thread crea un archivo .TXT con la descripción
```

**4. Uso de SwingWorker (hilo de fondo en Swing)**

```java
// AppEscritorioGUI.java — LÍNEAS 360-393
SwingWorker<List<Proceso>, Void> worker = new SwingWorker<>() {
    @Override
    protected List<Proceso> doInBackground() {
        // Esto corre en un hilo separado (NO bloquea la UI)
        return soService.obtenerProcesos(cantidad, criterio);
    }
    @Override
    protected void done() {
        // Esto corre en el hilo de la UI cuando termina
        procesosCapturados = get();
        actualizarTablaProcesos(procesosCapturados);
    }
};
worker.execute();
```

> 💡 **¿Por qué SwingWorker?** PowerShell tarda unos segundos. Si se llamara en el hilo principal, la ventana se congelaría. SwingWorker ejecuta la tarea en segundo plano.

---

## 👤 PERSONA 4 — Simulador Web Round Robin (App 2)

### ¿Qué explica?
El corazón del simulador: el algoritmo Round Robin, los Web Workers, los estados y los reportes.

### Puntos a cubrir

**1. Consumo del REST XML y carga de catálogos**

```javascript
// app.js — LÍNEAS 109-177
async function loadCatalogos() {
    const res = await fetch(`${API_URL}/catalogos`); // GET /api/catalogos
    const catalogos = await res.json();
    // Llena el <select> con los catálogos disponibles
}
```

> 💡 La web sincroniza automáticamente cada 3 segundos con `verificarNuevosCatalogos()`. Si se crea un nuevo catálogo en la app de escritorio, aparece automáticamente en el simulador.

**2. Preparación de la cola de LISTOS (cálculo de tiempos)**

```javascript
// app.js — LÍNEAS 292-316
procesosListos = procesos.map((p, index) => {
    let descLen = p.descripcion ? p.descripcion.length : 12;
    
    const tl = index === 0 ? 0 : tlAccum + 1;  // T.L = 0 si es el primero
    tlAccum = tl;
    
    const rafagaTotal = quantumTH * descLen;    // TR = TH × #caracteres
    
    return {
        rafaga: rafagaTotal,          // Tiempo total que necesita la CPU
        rafagaRestante: rafagaTotal,  // Cuánto le falta
        tiempoLlegada: tl,            // Cuándo entra a la cola
        ejecuciones: 0,               // Cuántas veces ha usado la CPU
        ...
    };
});
```

**3. El algoritmo Round Robin — Motor principal (Web Worker)**

La simulación usa un **Web Worker** (hilo JavaScript separado) que manda "ticks" cada 80ms sin bloquear la interfaz:

```javascript
// app.js — LÍNEAS 323-349
const workerCode = `
    let timer = setInterval(() => {
        if (!isPaused) self.postMessage({ type: 'TICK' }); // Envía tick cada 80ms
    }, 80);
`;
worker = new Worker(URL.createObjectURL(new Blob([workerCode])));
worker.onmessage = handleWorkerTick; // Por cada tick, ejecuta la lógica RR
```

**4. La lógica de decisión en cada tick (CORAZÓN del Round Robin)**

```javascript
// app.js — LÍNEAS 369-438 — handleWorkerTick()

// CPU LIBRE → tomar siguiente proceso de LISTOS
if (!procesoEnEjecucion && procesosListos.length > 0) {
    procesoEnEjecucion = procesosListos.shift(); // FIFO de la cola
    procesoEnEjecucion.ejecuciones++;
}

// PROCESO EN CPU → descontar tiempo de ráfaga
procesoEnEjecucion.rafagaRestante -= avance;
tickAcumuladoParaQuantum += avance;

// CASO A: Terminó completamente → TERMINADO
if (procesoEnEjecucion.rafagaRestante <= 0) {
    procesosTerminados.push(procesoEnEjecucion);
}

// CASO B: Expulsivo y agotó Quantum → vuelve a LISTOS (Round Robin)
if (procesoEnEjecucion.prioridad === 0 && tickAcumuladoParaQuantum >= quantumTH) {
    procesosListos.push(procesoEnEjecucion); // Va al FINAL de la cola
    procesoEnEjecucion = null;              // CPU libre
}

// CASO C: No Expulsivo → No sale por quantum, corre hasta terminar
if (procesoEnEjecucion.prioridad === 1) {
    // Solo cuenta ejecuciones pero no se expulsa
}
```

> 💡 **Regla Round Robin**: Expulsivo → quantum agotado → vuelve al final de LISTOS. No Expulsivo → corre hasta terminar sin importar el quantum.

**5. Reportes y cálculo de métricas**

```javascript
// app.js — LÍNEAS 628-697
reporte.forEach(p => {
    const tf = p.tiempoFinalizacion;           // T.F = Quantum × #Ejecuciones (aprox.)
    const tr = tf - p.tiempoLlegada;           // TurnaRound = T.F - T.L
    const te = Math.max(0, tr - p.rafaga);     // Espera = T.R - Ráfaga
});
```

| Columna | Fórmula | Significado |
|---------|---------|-------------|
| **T.L** | Asignado al inicio | Tiempo de llegada a la cola |
| **R** | `TH × #caracteres` | Ráfaga (burst time) total |
| **P.R** | 0 ó 1 | Tipo de proceso |
| **T.R** | `T.F - T.L` | TurnaRound Time |
| **T.F** | Registrado al terminar | Tiempo de finalización |

**6. Zona de ESPERA (funcionalidad adicional)**

La zona de Espera permite mandar un proceso manualmente fuera de la CPU durante la simulación:

```javascript
// app.js — LÍNEAS 478-503
window.mandarAEspera = function(id) {
    procesosEspera.push(procesoEnEjecucion);
    procesoEnEjecucion = null;
};
window.reanudarDeEspera = function(id) {
    procesosListos.push(p); // Regresa a la cola de LISTOS
};
```

---

## 🎯 PREGUNTAS TÍPICAS DEL PROFESOR Y RESPUESTAS

### 1. ¿Qué es un proceso expulsivo y no expulsivo?
> **R**: Un proceso **expulsivo (prioridad 0)** puede ser sacado de la CPU cuando agota su quantum; es un proceso de usuario. Un **No Expulsivo (prioridad 1)** es un proceso del sistema operativo que NO puede ser interrumpido por el quantum; corre hasta terminar su ráfaga completa.

### 2. ¿Cómo funciona Round Robin en el código?
> **R**: La cola de LISTOS es un array JavaScript que se maneja como FIFO. Cada tick descontamos tiempo al proceso en CPU. Si es Expulsivo y agota el quantum (`tickAcumuladoParaQuantum >= quantumTH`), lo mandamos al final de LISTOS con `push()`. Si termina su ráfaga, va a TERMINADOS.

### 3. ¿Para qué sirven los hilos (Thread) en Java?
> **R**: Cada proceso tiene su propio `Thread` en Java que crea un archivo `.txt` y escribe su descripción **carácter por carácter**, esperando `TH` milisegundos entre cada uno. Esto simula el trabajo que haría el proceso usando la CPU.

### 4. ¿Por qué usaron H2 y no MySQL?
> **R**: H2 es una base de datos en memoria que no requiere instalación. Para un laboratorio/simulación es ideal. En producción se cambiaría a MySQL o PostgreSQL simplemente cambiando las propiedades en `application.properties`.

### 5. ¿Cómo se comunican las dos aplicaciones?
> **R**: La App 1 (escritorio) guarda catálogos en H2 a través de Spring Data JPA. La App 2 (web) consume el endpoint REST `GET /api/catalogos` para obtener esos catálogos en JSON/XML. Ambas comparten el mismo backend Spring Boot.

### 6. ¿Qué es TH?
> **R**: TH es el **tiempo en milisegundos** que tarda el proceso en copiar un carácter. También equivale al **quantum** de Round Robin. La ráfaga total de un proceso es `TR = TH × cantidad_de_caracteres_de_la_descripción`.

### 7. ¿Qué es el TurnaRound?
> **R**: TurnaRound (T.R) es el tiempo total que un proceso pasa en el sistema, desde que llega hasta que termina: `TR = Tiempo_Finalización - Tiempo_Llegada`. Incluye tanto el tiempo en CPU como el tiempo esperando en la cola.

### 8. ¿Por qué existe la zona de Espera si el modelo de estados no la tiene?
> **R**: La zona de Espera simula una interrupción de I/O manual, permitiendo al usuario demostrar en vivo que puede sacar un proceso de la CPU y reanudarlo. Es una extensión pedagógica del modelo básico.

---

## 📝 FLUJO DE DEMOSTRACIÓN EN VIVO

```
1. Mostrar la aplicación iniciándose con el .bat
   → Se abre la ventana Swing + el servidor en http://localhost:8080

2. En App Escritorio (Swing):
   → Capturar 5 procesos por CPU
   → Mostrar la tabla: algunos son SYSTEM (No Expulsivos), otros de usuario (Expulsivos)
   → Escribir nombre del catálogo y guardarlo en BD

3. Abrir el Simulador Web en el navegador:
   → http://localhost:8080
   → Seleccionar el catálogo recién creado
   → Establecer TH = 100ms
   → Clic en "Iniciar"
   → Mostrar cómo los procesos se mueven entre colas
   → Pausar y reanudar
   → Ir a la pestaña Reportes y mostrar la tabla + gráfica

4. Mostrar el REST XML:
   → http://localhost:8080/api/catalogos/xml
   → Explicar que este es el puente entre las dos aplicaciones
```

---

## ⏱️ DISTRIBUCIÓN DE TIEMPO (20 min total)

```
P1 — Arquitectura y arranque            4 min
P2 — Captura del SO                     4 min
P3 — REST + Swing GUI                   5 min
P4 — Simulador RR + Reportes            5 min
Preguntas del profesor                  2 min
```

---

*Mucha suerte equipo! 🚀 — Universidad de Córdoba · S.O. 2026-II*
