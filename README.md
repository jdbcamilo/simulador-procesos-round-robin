# 🖥️ Simulador de Gestión de Procesos — Planificación Round Robin
### Sistema Híbrido: Aplicación de Escritorio (Generador & Catálogos) + Web Service REST (XML) + Simulador Web Interactivo

<div align="center">

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.0-brightgreen?style=for-the-badge&logo=springboot&logoColor=white)
![Swing FlatLaf](https://img.shields.io/badge/GUI-Swing_FlatLaf_Dark-blueviolet?style=for-the-badge)
![H2 Database](https://img.shields.io/badge/Database-H2_In--Memory-blue?style=for-the-badge&logo=h2)
![REST XML](https://img.shields.io/badge/Web_Service-REST_XML-ff69b4?style=for-the-badge)
![Frontend](https://img.shields.io/badge/Frontend-HTML5_|_CSS3_Glassmorphism_|_ES6+-informational?style=for-the-badge&logo=javascript)
![Universidad de Córdoba](https://img.shields.io/badge/Unicórdoba-Ingeniería_de_Sistemas-red?style=for-the-badge)

<p align="center">
  <b>Universidad de Córdoba — Facultad de Ingenierías</b><br>
  Departamento de Ingeniería de Sistemas y Telecomunicaciones<br>
  Asignatura: <b>Sistemas Operativos</b> · Periodo <b>2026-II</b>
</p>

</div>

---

## 📖 Tabla de Contenido
- [Visión General del Proyecto](#-visión-general-del-proyecto)
- [Arquitectura del Sistema](#-arquitectura-del-sistema)
- [Componentes y Funcionalidades](#-componentes-y-funcionalidades)
  - [Aplicación 1: Escritorio (Generador y Catalogador)](#1-aplicación-de-escritorio-generador-de-procesos)
  - [Servicio Web REST (XML)](#2-servicio-web-rest-xml)
  - [Aplicación 2: Web (Simulador Round Robin)](#3-aplicación-web-simulador-round-robin)
- [Fundamento Teórico y Fórmulas](#-fundamento-teórico-y-fórmulas)
- [Estructura del Proyecto](#-estructura-del-proyecto)
- [Requisitos Previos](#-requisitos-previos)
- [Instalación y Puesta en Marcha](#-instalación-y-puesta-en-marcha)
- [Documentación del Servicio Web (XML)](#-documentación-del-servicio-web-xml)
- [Documentos Académicos Complementarios](#-documentos-académicos-complementarios)
- [Créditos y Licencia](#-créditos-y-licencia)

---

## 🎯 Visión General del Proyecto

Este software es una **solución integral para la gestión y simulación de planificación de procesos del procesador** bajo el algoritmo **Round Robin (RR)**, combinando tecnologías de nivel empresarial:

1. **Lectura en tiempo real** de los procesos activos del sistema operativo host (Windows) mediante comandos nativos del kernel/PowerShell.
2. **Catalogación y persistencia** estructurada en base de datos H2 con clasificación de prioridad y expulsividad (preemptivo vs. no preemptivo).
3. **Interoperabilidad vía API REST en XML** exponiendo colecciones de procesos catalogados según especificaciones formales.
4. **Simulación visual de alta fidelidad** en un cliente web interactivo con colas dinámicas de estados (Listo ➔ Ejecución ➔ Terminado), diagrama de Gantt en tiempo real, gráficas estadísticas y exportación de reportes.

---

## 🏗 Arquitectura del Sistema

El proyecto implementa un patrón desacoplado y desacoplamiento limpio impulsado por **Spring Boot**, donde la GUI de escritorio y el servidor web conviven de manera coordinada.

```
+----------------------------------------------------------------------------------+
|                             SISTEMA OPERATIVO HOST                               |
|                  (Procesos reales: explorer.exe, chrome.exe, svchost...)         |
+----------------------------------------------------------------------------------+
                                        │
                                        ▼ (Lectura PowerShell / Tasklist)
+----------------------------------------------------------------------------------+
|                    APLICACIÓN 1: ESCRITORIO (Java Swing FlatLaf)                 |
|  - Filtro por CPU / Memoria (Top N procesos)                                    |
|  - Detección de PID, Nombre, Usuario, Descripción, Prioridad (0/1)              |
|  - Módulo de Catalogación (Número y Nombre descriptivo)                         |
+----------------------------------------------------------------------------------+
                                        │
                                        ▼
+----------------------------------------------------------------------------------+
|                 CAPA DE PERSISTENCIA & CONTROL (Spring Boot + JPA)               |
|  - Base de Datos H2 embebida (Tablas: CATALOGOS, PROCESOS)                      |
|  - Repositorios Spring Data JPA                                                  |
|  - Controlador REST (`/api/catalogos/xml`)                                       |
+----------------------------------------------------------------------------------+
                                        │
                                        ▼ HTTP GET / Accept: application/xml
+----------------------------------------------------------------------------------+
|                   APLICACIÓN 2: CLIENTE WEB (Simulador Round Robin)              |
|  - Parser nativo de XML (DOMParser)                                              |
|  - Selector de Catálogo & Configuración de Quantum (1s - 10s)                    |
|  - Cola de Estados: [LISTO] ➔ [EJECUCIÓN] ➔ [TERMINADO]                          |
|  - Manejo de Procesos Expulsivos (P=0) vs No Expulsivos (P=1)                    |
|  - Diagrama de Gantt en vivo con línea de tiempo                                 |
|  - Gráfica de TurnAround Time & Tabla de Métricas (T_e, T_r, CPU %, Throughput)  |
|  - Exportación a PDF y CSV                                                       |
+----------------------------------------------------------------------------------+
```

---

## 🚀 Componentes y Funcionalidades

### 1. Aplicación de Escritorio (Generador de Procesos)
- **Tecnología:** Java Swing enriquecido con el tema oscuro profesional **FlatLaf Dark**.
- **Inspección de Procesos Reales:** Lee los procesos en ejecución del sistema operativo mediante comandos nativos optimizados de Windows.
- **Criterios de Selección:** Permite especificar el número de procesos a capturar ($N$) y el criterio de ordenamiento:
  - Mayor uso de **CPU**
  - Mayor uso de **Memoria RAM**
- **Atributos extraídos por cada proceso:**
  - `PID`: Identificador único del proceso en el SO.
  - `Nombre`: Nombre del ejecutable (ej. `chrome.exe`, `code.exe`).
  - `Usuario`: Identidad de seguridad (`SYSTEM`, `LOCAL SERVICE`, nombre de usuario).
  - `Descripción`: Detalle del ejecutable obtenido del archivo binario.
  - `Prioridad`: 
    - `1 = No Expulsivo`: Procesos críticos del sistema operativo (`SYSTEM`, `LOCAL SERVICE`, servicios de red). No pueden ser desalojados de la CPU hasta que terminen su ráfaga.
    - `0 = Expulsivo`: Procesos normales de usuario. Si se les agota el Quantum, son desalojados y retornan a la cola de listos.
- **Gestión de Catálogos:** Permite asignar un **número de catálogo** y un **nombre descriptivo** para almacenar el lote en la base de datos H2.
- **Vista tabular interactiva:** Tabla con colores por prioridad, barras de desplazamiento y retroalimentación de estado.

### 2. Servicio Web REST (XML)
- **Tecnología:** Spring WebMVC con `jackson-dataformat-xml` y `jakarta.xml.bind`.
- **Ruta principal:** `GET /api/catalogos/xml`
- **Ruta por ID:** `GET /api/catalogos/{id}/xml`
- **Mapeo:** Produce respuestas con cabecera `Content-Type: application/xml;charset=UTF-8` conteniendo la estructura completa del catálogo y la lista anidada de procesos con sus atributos de planificación:
  - Tiempo de llegada ($T_l$)
  - Ráfaga de CPU ($T_b$)
  - Prioridad / Expulsividad ($0$ o $1$)

### 3. Aplicación Web (Simulador Round Robin)
- **Tecnología:** HTML5 semántico, CSS3 con estética **Glassmorphism / Neon Cyberpunk**, y Vanilla JavaScript (ES6+ modular).
- **Consumo REST Dinámico:** Al iniciar, realiza una petición `fetch` asíncrona hacia el servicio web REST, procesa la respuesta XML usando el `DOMParser` nativo del navegador y carga los catálogos en el selector.
- **Configuración Dinámica:**
  - Selector de catálogo guardado.
  - Slider para ajustar el **Quantum ($q$)** entre 1 y 10 unidades de tiempo.
  - Control de velocidad de simulación (Normal, Rápida, Pausada, Paso a Paso).
- **Modelo de Estados de Procesos:**
  - 🟡 **Cola de Listos (Ready Queue):** Procesos ordenados cronológicamente esperando turno de CPU.
  - 🟢 **En Ejecución (Running State):** El proceso asignado a la CPU.
  - 🔴 **Terminados (Finished State):** Procesos que han consumido el 100% de su ráfaga de CPU.
  - 🔄 **Desalojo Expulsivo:** Si el proceso tiene **Prioridad 0 (Expulsivo)** y su ráfaga pendiente excede el Quantum, el simulador lo desaloja de la CPU, registra la partición en el diagrama de Gantt y lo reinserta al final de la cola de listos.
  - 🛡️ **Protección No Expulsiva:** Si el proceso tiene **Prioridad 1 (No Expulsivo / Sistema Operativo)**, retiene la CPU ininterrumpidamente hasta finalizar su ráfaga completa, ignorando el corte de Quantum.
- **Visualización y Reportes:**
  - 📊 **Diagrama de Gantt Interactivo:** Muestra los bloques temporales de ejecución por proceso con su PID y color distintivo.
  - 📈 **Gráfica de Barras:** Comparativa de Tiempo de Retorno (TurnAround Time) por cada proceso.
  - 📋 **Tabla de Métricas de Rendimiento:**
    - Tiempo de Espera ($T_e$)
    - Tiempo de Retorno ($T_r$)
    - Tiempo de Respuesta ($T_{resp}$)
    - Promedios globales
    - Porcentaje de Utilización de CPU
    - Rendimiento del Sistema (Throughput)
  - 📄 **Exportación:** Botones para generar reportes en formato **PDF** y **CSV** listos para entregar.

---

## 🧮 Fundamento Teórico y Fórmulas

El algoritmo **Round Robin con Prioridad Expulsiva/No Expulsiva** se rige por las siguientes definiciones matemáticas implementadas en el simulador:

| Métrica | Símbolo | Fórmula | Definición |
|---|---|---|---|
| **Tiempo de Ráfaga Restante** | $T_{rem}$ | $T_{rem}(t) = T_b - t_{ejecutado}$ | Cantidad de CPU que le resta al proceso. |
| **Tiempo de Retorno (TurnAround)** | $T_r$ | $T_r = T_{finalización} - T_{llegada}$ | Tiempo total transcurrido desde que el proceso llega hasta que finaliza. |
| **Tiempo de Espera** | $T_e$ | $T_e = T_r - T_b$ | Tiempo total que el proceso permaneció en la cola de listos sin usar CPU. |
| **Tiempo de Respuesta** | $T_{resp}$ | $T_{resp} = T_{primera\_ejecución} - T_{llegada}$ | Tiempo transcurrido hasta que el proceso entra por primera vez a la CPU. |
| **Tiempo de Espera Promedio** | $\overline{T_e}$ | $\overline{T_e} = \frac{1}{N} \sum_{i=1}^{N} T_{e_i}$ | Métrica global de equidad del planificador. |
| **Tiempo de Retorno Promedio** | $\overline{T_r}$ | $\overline{T_r} = \frac{1}{N} \sum_{i=1}^{N} T_{r_i}$ | Eficiencia global del sistema. |
| **Utilización de CPU** | $\% CPU$ | $\% CPU = \frac{\sum T_{b_i}}{T_{total}} \times 100$ | Porcentaje de tiempo que la CPU estuvo ocupada procesando trabajo útil. |
| **Rendimiento (Throughput)** | $Th$ | $Th = \frac{N}{T_{total}}$ | Procesos completados por unidad de tiempo. |

---

## 📁 Estructura del Proyecto

```plaintext
simulador-procesos/
│
├── src/
│   ├── main/
│   │   ├── java/com/unicordoba/simulador/
│   │   │   ├── SimuladorProcesosApplication.java    # Entrada principal Spring Boot + Launcher Swing
│   │   │   ├── controller/
│   │   │   │   └── CatalogoController.java          # Endpoint REST XML (/api/catalogos)
│   │   │   ├── model/
│   │   │   │   ├── Catalogo.java                    # Entidad JPA para catálogos
│   │   │   │   ├── Proceso.java                     # Entidad JPA para procesos
│   │   │   │   └── xml/                             # Clases DTO con anotaciones JAXB/Jackson XML
│   │   │   │       ├── CatalogoXml.java
│   │   │   │       ├── CatalogoListXml.java
│   │   │   │       └── ProcesoXml.java
│   │   │   ├── repository/
│   │   │   │   ├── CatalogoRepository.java          # Interfaz Spring Data JPA
│   │   │   │   └── ProcesoRepository.java
│   │   │   ├── service/
│   │   │   │   ├── CatalogoService.java             # Lógica de negocio y persistencia
│   │   │   │   └── OsProcessReaderService.java      # Lector de procesos nativos del SO (PowerShell)
│   │   │   └── gui/
│   │   │       └── AppEscritorioGUI.java            # Interfaz gráfica Swing FlatLaf Dark
│   │   │
│   │   └── resources/
│   │       ├── application.properties               # Configuración JPA, H2 y puerto 8080
│   │       └── static/                              # Frontend web del simulador
│   │           ├── index.html                       # Dashboard interactivo del simulador
│   │           ├── styles.css                       # Diseño moderno Glassmorphism
│   │           └── app.js                           # Algoritmo Round Robin, Gantt y Métricas
│   │
│   └── test/                                        # Pruebas unitarias de controladores y servicios
│
├── iniciar_simulador.bat                            # Script de inicio en un solo clic para Windows
├── pom.xml                                          # Dependencias Maven y configuración del build
├── mvnw / mvnw.cmd                                  # Maven Wrapper multiplataforma
├── GUIA_SUSTENTACION.md                             # Libreto y preguntas de sustentación oral
├── INFORME_TECNICO.md                               # Informe formal de laboratorio universitario
└── README.md                                        # Documentación principal del repositorio
```

---

## ⚙️ Requisitos Previos

Asegúrate de contar con los siguientes elementos instalados en tu equipo:

1. **Java Development Kit (JDK) 21** o superior.
   - Verifica ejecutando:
     ```bash
     java -version
     ```
2. **Git** (opcional, para clonar el repositorio).
3. **Sistema Operativo:** Windows 10/11 (recomendado para la lectura nativa completa de procesos con PowerShell) o Linux.
4. **Navegador Web Moderno:** Google Chrome, Mozilla Firefox, Microsoft Edge o Brave.

---

## 🛠️ Instalación y Puesta en Marcha

### Opción 1: Ejecución Rápida en un Clic (Recomendada para Windows)
Haz doble clic sobre el archivo ejecutable por lotes:
```plaintext
iniciar_simulador.bat
```
Este script:
1. Valida automáticamente la presencia de Java 21 en tu sistema.
2. Descarga dependencias requeridas mediante el Maven Wrapper (`mvnw`).
3. Levanta el servidor Spring Boot en el puerto `8080`.
4. Abre automáticamente la **Aplicación de Escritorio (Swing FlatLaf)** en tu pantalla.
5. Pone a disposición el **Simulador Web** en `http://localhost:8080`.

---

### Opción 2: Compilación y Ejecución Manual desde la Terminal

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/jdbcamilo/simulador-procesos-round-robin.git
   cd simulador-procesos-round-robin/simulador-procesos
   ```

2. **Compilar y empaquetar el proyecto:**
   - En Windows (PowerShell / CMD):
     ```cmd
     .\mvnw.cmd clean package -DskipTests
     ```
   - En Linux / macOS:
     ```bash
     chmod +x mvnw
     ./mvnw clean package -DskipTests
     ```

3. **Ejecutar la aplicación integrada:**
   - En Windows:
     ```cmd
     .\mvnw.cmd spring-boot:run
     ```
   - En Linux / macOS:
     ```bash
     ./mvnw spring-boot:run
     ```

4. **Acceder a los módulos del sistema:**
   - 🖥️ **Aplicación de Escritorio:** Se abrirá automáticamente como ventana nativa.
   - 🌐 **Aplicación Web:** Abre tu navegador e ingresa a:
     ```
     http://localhost:8080
     ```
   - 📡 **Servicio Web REST (XML):**
     ```
     http://localhost:8080/api/catalogos/xml
     ```
   - 🗄️ **Consola de Base de Datos H2:**
     ```
     http://localhost:8080/h2-console
     JDBC URL: jdbc:h2:mem:simuladordb
     Usuario:  sa
     Password: (dejar vacío)
     ```

---

## 📡 Documentación del Servicio Web (XML)

### Endpoint: Obtener todos los catálogos en XML
- **URL:** `/api/catalogos/xml`
- **Método HTTP:** `GET`
- **Cabecera de Respuesta:** `Content-Type: application/xml`

#### Ejemplo de Respuesta XML:
```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<catalogos>
    <catalogo>
        <id>1</id>
        <numeroCatalogo>101</numeroCatalogo>
        <nombre>Lote Procesos CPU Intensivos</nombre>
        <fechaCreacion>2026-10-19T14:30:00</fechaCreacion>
        <procesos>
            <proceso>
                <id>1</id>
                <pid>14220</pid>
                <nombre>chrome.exe</nombre>
                <usuario>Lenovo</usuario>
                <descripcion>Google Chrome</descripcion>
                <prioridad>0</prioridad>
                <tiempoLlegada>0</tiempoLlegada>
                <tiempoRafaga>8</tiempoRafaga>
            </proceso>
            <proceso>
                <id>2</id>
                <pid>4</pid>
                <nombre>System</nombre>
                <usuario>SYSTEM</usuario>
                <descripcion>NT Kernel &amp; System</descripcion>
                <prioridad>1</prioridad>
                <tiempoLlegada>1</tiempoLlegada>
                <tiempoRafaga>12</tiempoRafaga>
            </proceso>
        </procesos>
    </catalogo>
</catalogos>
```

---

## 📚 Documentos Académicos Complementarios

En la raíz del proyecto encontrarás dos documentos elaborados para la sustentación y entrega de la asignatura:

- 📋 [GUIA_SUSTENTACION.md](file:///c:/Users/Lenovo/Desktop/U/SEMESTRE%207/S.O/Similador%20de%20procesos/simulador-procesos/GUIA_SUSTENTACION.md): Libreto paso a paso para la exposición oral de 20 minutos ante el docente, con respuestas preparadas a las preguntas técnicas más difíciles.
- 📄 [INFORME_TECNICO.md](file:///c:/Users/Lenovo/Desktop/U/SEMESTRE%207/S.O/Similador%20de%20procesos/simulador-procesos/INFORME_TECNICO.md): Informe formal en formato artículo científico/técnico con marco conceptual, diagramas de arquitectura, tablas de pruebas, análisis de casos y referencias bibliográficas normalizadas (Silberschatz, Tanenbaum, Stallings).

---

## 👨‍💻 Créditos y Licencia

- **Institución:** Universidad de Córdoba
- **Programa:** Ingeniería de Sistemas
- **Asignatura:** Sistemas Operativos (2026-II)
- **Autor / Desarrollador:** [jdbcamilo](https://github.com/jdbcamilo)
- **Licencia:** Este proyecto se distribuye bajo la licencia académica para fines educativos y de investigación.
