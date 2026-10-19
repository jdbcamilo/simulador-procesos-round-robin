# INFORME TÉCNICO
## Simulador de Planificación de Procesos — Algoritmo Round Robin
### Universidad de Córdoba · Ingeniería de Sistemas · Sistemas Operativos · 2026-II

---

| | |
|---|---|
| **Asignatura** | Sistemas Operativos |
| **Laboratorio** | Gestión de Procesos |
| **Algoritmo implementado** | Round Robin (Expulsivo / No Expulsivo) |
| **Fecha de entrega** | 20 de octubre de 2026 |
| **Institución** | Universidad de Córdoba |

---

## 1. Introducción

El presente informe documenta el diseño, implementación y funcionamiento del sistema **SYSTEM — Simulador de Procesos**, desarrollado como parte del laboratorio de Gestión de Procesos para la asignatura de Sistemas Operativos.

El sistema está compuesto por dos aplicaciones interconectadas:

1. **Aplicación 1 — Generador de Procesos (Escritorio)**: Captura procesos reales del sistema operativo, los cataloga y los persiste en base de datos, exponiendo un servicio web REST en formato XML.

2. **Aplicación 2 — Simulador Round Robin (Web)**: Consume el servicio REST para obtener catálogos de procesos y simula la planificación de CPU mediante el algoritmo Round Robin, distinguiendo entre procesos expulsivos y no expulsivos.

---

## 2. Objetivos

### 2.1 Objetivo General
Desarrollar un sistema de dos capas que capture procesos reales del sistema operativo, los catalogue en una base de datos, y simule su planificación mediante el algoritmo Round Robin, implementando el modelo de estados (Listo → Ejecución → Terminado) con procesos ejecutándose en hilos independientes.

### 2.2 Objetivos Específicos
- Capturar procesos reales del sistema operativo usando PowerShell y la API Java `ProcessHandle`
- Clasificar los procesos como Expulsivos (usuario) o No Expulsivos (sistema operativo)
- Exponer los catálogos de procesos mediante un servicio web REST en formato XML
- Implementar el algoritmo Round Robin con estructuras de datos dinámicas (colas/listas)
- Calcular métricas de planificación: ráfaga, tiempo de llegada, turnaround y tiempo de finalización
- Ejecutar la actividad de cada proceso en su propio hilo Java independiente
- Generar reportes visuales (tablas y gráficas) de la simulación

---

## 3. Marco Teórico

### 3.1 Algoritmo Round Robin

El **Round Robin** es un algoritmo de planificación de CPU diseñado específicamente para sistemas de tiempo compartido. Su principio fundamental es asignar a cada proceso un período de tiempo fijo llamado **quantum** o **time slice**. Cuando el quantum se agota, el proceso es interrumpido y colocado al final de la cola de procesos listos, permitiendo que el siguiente proceso en la cola tome la CPU.

**Características principales:**
- Es un algoritmo **apropiativo** (expulsivo): puede interrumpir un proceso en ejecución
- Garantiza **equidad**: ningún proceso espera indefinidamente
- Adecuado para sistemas interactivos de tiempo real
- El rendimiento depende del valor del quantum elegido

### 3.2 Procesos Expulsivos vs. No Expulsivos

En el contexto de este simulador:

| Tipo | Prioridad | Comportamiento | Ejemplo |
|------|-----------|---------------|---------|
| **Expulsivo** | 0 | Sale de CPU al agotar el quantum; regresa a la cola de LISTOS | Procesos de usuario |
| **No Expulsivo** | 1 | No puede ser interrumpido por el quantum; corre hasta finalizar | Procesos del SO: `svchost`, `lsass`, `csrss` |

### 3.3 Modelo de Estados

```
         Quantum agotado (solo Expulsivos)
              ┌─────────────────────┐
              ↓                     │
  [LISTO] ──→ [EJECUCIÓN] ──────→ [TERMINADO]
    ↑                    Ráfaga completa
    │
 [ESPERA] (interrupción manual / I/O)
```

### 3.4 Fórmulas de Planificación

| Métrica | Fórmula | Descripción |
|---------|---------|-------------|
| **Ráfaga (R)** | `R = TH × n` | TH = quantum en ms; n = caracteres de la descripción |
| **Tiempo de Llegada (T.L)** | `TL[0] = 0; TL[i] = TL[i-1] + 1` | Orden de llegada a la cola de LISTOS |
| **Tiempo de Finalización (T.F)** | Registrado cuando `rafagaRestante ≤ 0` | Momento en que termina la simulación |
| **TurnaRound (T.R)** | `TR = T.F - T.L` | Tiempo total en el sistema |
| **Tiempo de Espera (T.E)** | `TE = TR - R` | Tiempo esperando en cola |

---

## 4. Arquitectura del Sistema

### 4.1 Vista General

```
┌─────────────────────────────────────────────────────────────┐
│              SISTEMA OPERATIVO (Windows)                     │
│  Procesos reales: svchost, chrome, Word, Idle, etc.         │
└──────────────────────────┬──────────────────────────────────┘
                           │ PowerShell Get-Process
                           ↓
┌─────────────────────────────────────────────────────────────┐
│          SPRING BOOT 4.1 — Puerto 8080                       │
│                                                              │
│  ┌──────────────────┐    ┌──────────────────────────────┐   │
│  │  AppEscritorioGUI│    │     CatalogoController        │   │
│  │  (Java Swing)    │    │     (REST API)                │   │
│  │                  │    │  GET  /api/catalogos/xml      │   │
│  │  PASO 1: Captura │    │  GET  /api/catalogos          │   │
│  │  PASO 2: Catálogo│    │  POST /api/catalogos          │   │
│  │  PASO 3: Hilos   │    │  POST /.../ejecutar-actividad │   │
│  └──────────────────┘    └──────────────────────────────┘   │
│            │                            │                    │
│            └──────────┬─────────────────┘                    │
│                       ↓                                      │
│              ┌──────────────────┐                            │
│              │  CatalogoService │                            │
│              │  SOService       │                            │
│              └────────┬─────────┘                            │
│                       ↓                                      │
│              ┌──────────────────┐                            │
│              │   H2 Database    │                            │
│              │   (En memoria)   │                            │
│              └──────────────────┘                            │
└─────────────────────────────────────────────────────────────┘
                           │ REST XML/JSON
                           ↓
┌─────────────────────────────────────────────────────────────┐
│           APLICACIÓN WEB (index.html + app.js)               │
│                                                              │
│  [Catálogos] → [Simulador RR] → [Reportes] → [XML REST]     │
│                                                              │
│  Cola LISTOS → [CPU/EJECUCIÓN] → Cola TERMINADOS            │
│                       ↓                                      │
│              Cola ESPERA (I/O Manual)                        │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Stack Tecnológico

| Componente | Tecnología | Versión | Justificación |
|-----------|-----------|---------|--------------|
| Backend / Framework | Spring Boot | 4.1.0 | IoC, REST, JPA integrados |
| Lenguaje | Java | 21 (LTS) | API `ProcessHandle`, lambdas, records |
| Base de Datos | H2 (en memoria) | Runtime | Sin instalación externa requerida |
| ORM | Spring Data JPA / Hibernate | Spring Boot | CRUD automático |
| GUI Escritorio | Java Swing + FlatLaf | 3.5.4 | Interfaz nativa con tema moderno |
| Serialización XML | Jackson XML + JAXB | Spring Boot | Anotaciones `@XmlRootElement` |
| Frontend Web | HTML5 + CSS3 + JavaScript ES6 | Vanilla | Sin dependencias de framework |
| Gráficas | Chart.js | CDN | Gráfica de barras TurnaRound |
| Build | Maven | 3.x | Gestión de dependencias |

---

## 5. Descripción Detallada de Componentes

### 5.1 Aplicación 1 — Generador de Procesos (Escritorio)

#### 5.1.1 `SistemaOperativoService.java` — Captura de Procesos Reales

Este servicio es el corazón de la App 1. Utiliza la API de Windows PowerShell para obtener los procesos activos del sistema operativo en tiempo real.

**Estrategia de captura:**

```
1. Construir script PowerShell con Get-Process
2. Filtrar procesos (excluir PID 0 e Idle)
3. Ordenar por CPU o MEMORIA (según criterio del usuario)
4. Seleccionar los N primeros
5. Exportar como CSV con ConvertTo-Csv
6. Parsear CSV en Java línea por línea
```

**Clasificación de procesos:**

El servicio identifica automáticamente si un proceso pertenece al sistema operativo verificando:
- El usuario del proceso es `SYSTEM`, `LOCAL SERVICE` o `NETWORK SERVICE`
- El nombre coincide con patrones del SO: `svchost`, `lsass`, `csrss`, `winlogon`, `dwm`, `smss`, etc.

**Fallback:** Si PowerShell no está disponible, se usa `ProcessHandle.allProcesses()` de Java 21.

#### 5.1.2 `CatalogoService.java` — Lógica de Negocio

Gestiona todas las operaciones sobre catálogos y procesos:

- **`crearCatalogo()`**: Captura procesos del SO y los agrupa en un catálogo nuevo
- **`guardarCatalogoConProcesos()`**: Persiste en H2 un catálogo con número y nombre personalizados
- **`generarCatalogosXml()`**: Construye manualmente el XML completo para el servicio REST
- **`ejecutarActividadEnHilos()`**: Lanza un `Thread` independiente por proceso

**Actividad de cada proceso (Hilo Java):**

```java
Thread t = new Thread(() -> {
    File archivo = new File(outputDir, proceso.getNombre() + "_" + proceso.getPid() + ".txt");
    try (FileWriter writer = new FileWriter(archivo)) {
        for (int i = 0; i < descripcion.length(); i++) {
            writer.write(descripcion.charAt(i));  // Copia un carácter
            writer.flush();
            Thread.sleep(th);  // Espera TH milisegundos
        }
    }
}, "Hilo-Proceso-" + proceso.getPid());
t.start();
```

#### 5.1.3 `CatalogoController.java` — API REST

Expone los endpoints HTTP. Soporta negociación de contenido XML/JSON mediante el header `Accept`:

- `Accept: application/xml` → responde en XML
- `Accept: application/json` → responde en JSON

El endpoint `/api/catalogos/xml` siempre devuelve XML puro, diseñado para consumo directo desde el navegador o la App 2.

#### 5.1.4 `AppEscritorioGUI.java` — Interfaz Gráfica Swing

Ventana organizada en 3 pestañas principales con una consola de logs integrada:

**Pestaña 1 — Capturar y Catalogar:**
- `JSpinner` para cantidad de procesos (1-100)
- `JComboBox` para criterio (CPU / MEMORIA)
- `JTable` de solo lectura con procesos capturados
- `JTextField` para número y nombre del catálogo
- `JSpinner` para valor TH

**Pestaña 2 — Catálogos en BD:**
- `JTable` con todos los catálogos guardados
- Botones: Refrescar, Ver Procesos, Ver XML, Eliminar

**Consola de Logs:**
- `JTextArea` de fondo oscuro con tipografía Consolas
- Registra cada acción con timestamp `[HH:mm:ss]`
- Uso de `SwingUtilities.invokeLater()` para thread-safety

---

### 5.2 Aplicación 2 — Simulador Round Robin (Web)

#### 5.2.1 Carga de Catálogos y Sincronización

La app web sincroniza automáticamente con el backend cada 3 segundos:

```javascript
setInterval(() => {
    if (!isSimulating) verificarNuevosCatalogos();
}, 3000);
```

Si se detecta un nuevo catálogo creado desde la App de Escritorio, aparece automáticamente con una notificación toast.

#### 5.2.2 Preparación de la Cola de LISTOS

Al iniciar la simulación, cada proceso se transforma en un objeto con sus métricas de planificación:

```javascript
{
    rafaga: TH * caracteres_descripcion,  // Ráfaga total en ms
    rafagaRestante: rafagaTotal,           // Contador regresivo
    tiempoLlegada: tl,                     // T.L calculado secuencialmente
    ejecuciones: 0,                        // Contador de accesos a CPU
    tiempoFinalizacion: null               // Se registra al terminar
}
```

#### 5.2.3 Motor de Simulación — Web Worker

Para no bloquear la interfaz gráfica durante la simulación, se usa un **Web Worker** (hilo JavaScript separado) que emite "ticks" cada 80ms. El hilo principal recibe cada tick y ejecuta la lógica Round Robin.

**Flujo de un tick:**

```
TICK recibido
    │
    ├─ ¿CPU libre? → Tomar proceso de procesosListos.shift() (FIFO)
    │
    └─ CPU ocupada:
        ├─ Descontar tiempo de ráfaga
        ├─ ¿rafagaRestante ≤ 0? → Mover a TERMINADOS (registro T.F)
        └─ ¿Expulsivo y tickAcumulado ≥ quantumTH?
            ├─ SÍ → procesosListos.push() (vuelve al final: Round Robin)
            └─ NO → Continúa en CPU (No Expulsivo o quantum no agotado)
```

#### 5.2.4 Visualización en Tiempo Real

Las cuatro zonas de la GUI se actualizan en cada tick:

| Zona | Estructura de datos | Descripción |
|------|---------------------|-------------|
| **LISTOS** | `procesosListos[]` (array FIFO) | Cola de procesos esperando CPU |
| **EJECUCIÓN** | `procesoEnEjecucion` (objeto único) | Proceso usando la CPU ahora |
| **ESPERA** | `procesosEspera[]` | Procesos pausados manualmente (I/O) |
| **TERMINADO** | `procesosTerminados[]` | Procesos que completaron su ráfaga |

Cada proceso se muestra como una **tarjeta (card)** con: nombre, PID, badge de prioridad, quantum restante, número de ejecuciones, ráfaga restante y barra de progreso.

#### 5.2.5 Diagrama de Gantt

Se genera dinámicamente registrando bloques de ejecución `{name, start, end}`. Cada vez que un proceso sale de la CPU (por terminación o expulsión), el bloque se cierra y se renderiza como una barra en el eje de tiempo.

#### 5.2.6 Reportes

**Listado de Ejecución (todos los procesos terminados):**

| P | T.L | R | P.R | T.R | T.F | Ejecuciones |
|---|-----|---|-----|-----|-----|-------------|
| Nombre del proceso | Tiempo llegada (ms) | Ráfaga total (ms) | 0/1 | TurnaRound (ms) | Tiempo final (ms) | Nro. veces en CPU |

**Listado de Procesos No Expulsivos:** misma tabla filtrada solo para `prioridad === 1`.

**Gráfica TurnaRound:** gráfica de barras horizontal (Chart.js) con `Proceso vs T.R (ms)`.

**Métricas de Resumen:**
- Promedio de TurnaRound
- Promedio de Tiempo de Espera
- Utilización de CPU
- Throughput (procesos por segundo)

---

## 6. Flujo de Operación Completo

```
[Usuario]
    │
    ▼
[App Escritorio — Paso 1]
    Selecciona cantidad N y criterio (CPU / MEMORIA)
    → SistemaOperativoService.obtenerProcesos(N, criterio)
    → PowerShell: Get-Process | Sort | ConvertTo-Csv
    → Tabla de procesos en pantalla
    │
    ▼
[App Escritorio — Paso 2]
    Usuario define N° de catálogo y nombre
    → CatalogoService.guardarCatalogoConProcesos()
    → INSERT INTO catalogos + INSERT INTO procesos (H2 BD)
    → Catálogo disponible vía REST: /api/catalogos/xml
    │
    ▼
[App Web — Simulador]
    Selecciona catálogo del dropdown (cargado desde REST)
    Define TH (ms por carácter = quantum)
    Clic en "Iniciar"
    → Fetch GET /api/catalogos/{id}/procesos
    → Calcula ráfagas y tiempos de llegada
    → POST /api/catalogos/{id}/ejecutar-actividad?th=TH (lanza Threads Java)
    → Inicia Web Worker (ticks cada 80ms)
    │
    ▼
[Simulación Round Robin en tiempo real]
    Cola LISTOS → [CPU] → Cola TERMINADOS
    Expulsivos: al agotar quantum → vuelven a LISTOS
    No Expulsivos: corren hasta finalizar
    │
    ▼
[Reportes]
    Tabla de ejecución + Tabla No Expulsivos + Gráfica TurnaRound
```

---

## 7. Pruebas y Resultados

### 7.1 Escenario de Prueba

**Parámetros:**
- 5 procesos capturados (mezcla de procesos del SO y de usuario)
- TH = 100 ms (quantum = 100 ms por carácter)

**Ejemplo de Datos:**

| Proceso | PID | Tipo | Descripción (chars) | Ráfaga (R) | T.L |
|---------|-----|------|----------------------|-----------|-----|
| svchost | 1234 | No Expulsivo (1) | "Proceso de ejecucion svchost" (30) | 3000 ms | 0 |
| chrome | 5678 | Expulsivo (0) | "Google Chrome Browser" (22) | 2200 ms | 1 |
| explorer | 9012 | Expulsivo (0) | "Windows Explorer" (17) | 1700 ms | 2 |
| MsMpEng | 3456 | No Expulsivo (1) | "Microsoft Defender Antivirus" (29) | 2900 ms | 3 |
| Code | 7890 | Expulsivo (0) | "Visual Studio Code" (19) | 1900 ms | 4 |

**Comportamiento esperado con Round Robin (TH = 100ms):**

- `svchost` (No Expulsivo) entra primero y corre hasta terminar (3000ms) sin interrupciones
- `chrome` entra y corre 100ms → vuelve a LISTOS → cicla hasta completar sus 2200ms
- `explorer` y `Code` (Expulsivos) se alternan en la cola de LISTOS
- `MsMpEng` (No Expulsivo) corre sin interrupción cuando le toca la CPU

### 7.2 Verificación del Servicio REST

El endpoint `GET /api/catalogos/xml` devuelve:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<catalogos total="1">
  <catalogo id="1">
    <numeroCatalogo>1</numeroCatalogo>
    <nombreCatalogo>Lote de Procesos 1</nombreCatalogo>
    <criterioSeleccion>CPU</criterioSeleccion>
    <fechaCreacion>2026-10-19T14:00:00</fechaCreacion>
    <procesos cantidad="5">
      <proceso>
        <pid>1234</pid>
        <nombre>svchost</nombre>
        <usuario>SYSTEM</usuario>
        <descripcion>Proceso de ejecucion svchost</descripcion>
        <prioridad>1</prioridad>
        <tipoPrioridad>No Expulsivo</tipoPrioridad>
        <usoCpu>2.50</usoCpu>
        <usoMemoria>45.30</usoMemoria>
      </proceso>
      <!-- ... -->
    </procesos>
  </catalogo>
</catalogos>
```

---

## 8. Dificultades Encontradas y Soluciones

| Dificultad | Solución Implementada |
|-----------|----------------------|
| PowerShell devuelve caracteres especiales que rompen el parseo CSV | Implementación de `parseCSVLine()` con soporte para campos entre comillas |
| La descripción de algunos procesos tiene cientos de caracteres (ráfagas enormes) | Límite de 60 caracteres en `SistemaOperativoService` |
| La GUI Swing se congela al capturar procesos (tarea bloqueante) | Uso de `SwingWorker` para ejecutar la captura en hilo de fondo |
| Serialización de `LocalDateTime` a XML causa errores | Serialización manual con `StringBuilder` en `generarCatalogosXml()` |
| El Web Worker no puede acceder al DOM directamente | Uso del patrón message-passing: Worker emite `TICK`, hilo principal actualiza DOM |
| CORS bloquea peticiones del frontend al backend | `@CrossOrigin(origins = "*")` en el controller + `WebConfig.java` |

---

## 9. Conclusiones

1. **Se implementó exitosamente** el algoritmo Round Robin con distinción entre procesos expulsivos (usuario) y no expulsivos (sistema operativo), evidenciando en tiempo real las diferencias de comportamiento de planificación.

2. **La arquitectura de una sola aplicación Spring Boot** que sirve simultáneamente como backend REST, base de datos JPA y servidor de archivos estáticos web, demostró ser eficiente y de fácil despliegue sin necesidad de servidores externos.

3. **El uso de hilos Java (`Thread`) para la actividad de procesos** y **Web Workers JavaScript para la simulación** evidencia la aplicación práctica de concurrencia en ambos extremos del sistema.

4. **El modelo de estados Listo → Ejecución → Terminado** se implementó correctamente con estructuras de datos dinámicas (arrays JavaScript como colas FIFO), facilitando la comprensión visual del comportamiento del scheduler.

5. **La integración REST XML** entre la App de Escritorio y el Simulador Web demostró el concepto de microservicios y comunicación entre aplicaciones heterogéneas.

---

## 10. Referencias

- Silberschatz, A., Galvin, P. B., & Gagne, G. (2018). *Operating System Concepts* (10th ed.). Wiley.
- Oracle. (2023). *Java SE 21 API Documentation — ProcessHandle*. https://docs.oracle.com/en/java/docs
- Spring. (2024). *Spring Boot Reference Documentation 4.1*. https://spring.io/projects/spring-boot
- MDN Web Docs. (2024). *Web Workers API*. https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API
- PowerShell Docs. (2024). *Get-Process*. https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.management/get-process

---

*Documento generado para sustentación del laboratorio de Gestión de Procesos — Universidad de Córdoba — Sistemas Operativos 2026-II*
