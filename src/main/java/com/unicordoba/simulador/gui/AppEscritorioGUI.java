package com.unicordoba.simulador.gui;

import com.unicordoba.simulador.model.Catalogo;
import com.unicordoba.simulador.model.Proceso;
import com.unicordoba.simulador.service.CatalogoService;
import com.unicordoba.simulador.service.SistemaOperativoService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplicación 1: Interfaz Gráfica de Escritorio (Java Swing)
 * Generador y Gestor de Procesos del Sistema Operativo.
 *
 * Funcionalidades:
 * 1. Lee procesos reales del SO por mayor uso de CPU o memoria.
 * 2. Clasifica procesos: Prioridad 1 (No Expulsivos - SO) y 0 (Expulsivos - Usuario).
 * 3. Permite catalogar el conjunto obtenido con N° y nombre descriptivo.
 * 4. Guarda catálogos en la base de datos H2.
 * 5. Muestra el listado de procesos en tablas dinámicas.
 * 6. Ejecuta la actividad de cada proceso en su propio Thread (escribe descripción en TXT).
 * 7. Enlaza con el servicio web REST XML.
 */
public class AppEscritorioGUI extends JFrame {

    private final CatalogoService catalogoService;
    private final SistemaOperativoService soService;

    // Modelos y tablas
    private DefaultTableModel tablaProcesosModel;
    private JTable tablaProcesos;
    private DefaultTableModel tablaCatalogosModel;
    private JTable tablaCatalogos;

    // Componentes de control
    private JSpinner spinnerCantidad;
    private JComboBox<String> comboCriterio;
    private JTextField txtNumeroCatalogo;
    private JTextField txtNombreCatalogo;
    private JSpinner spinnerTH;
    private JTextArea txtLogs;
    private JLabel lblResumenProcesos;
    private JButton btnCapturar;
    private JButton btnGuardarBD;
    private JButton btnEjecutarHilos;

    // Lista en memoria de procesos capturados actualmente
    private List<Proceso> procesosCapturados = new ArrayList<>();

    public AppEscritorioGUI(CatalogoService catalogoService, SistemaOperativoService soService) {
        this.catalogoService = catalogoService;
        this.soService = soService;

        setTitle("SYSTEM — Aplicación 1: Generador de Procesos del SO (Escritorio)");
        setSize(1150, 800);
        setMinimumSize(new Dimension(950, 650));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initUI();
        cargarCatalogosGuardados();
        log("Aplicación 1 (Escritorio) lista y conectada al backend.");
        log("Servicio Web REST XML activo en: http://localhost:8080/api/catalogos/xml");
        log("Simulador Web disponible en: http://localhost:8080");
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(12, 14, 12, 14));
        mainPanel.setBackground(new Color(243, 244, 246));

        // 1. Header superior
        mainPanel.add(crearHeaderPanel(), BorderLayout.NORTH);

        // 2. Centro: Pestañas (Captura de procesos / Catálogos en BD)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabbedPane.addTab("⚡ 1. Capturar y Catalogar Procesos", crearPanelCaptura());
        tabbedPane.addTab("📂 2. Catálogos Guardados en Base de Datos", crearPanelCatalogosBD());
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // 3. Inferior: Consola de Logs y estado
        mainPanel.add(crearPanelLogs(), BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel crearHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 5));
        header.setBackground(new Color(17, 24, 39)); // Slate oscuro
        header.setBorder(new CompoundBorder(
                new LineBorder(new Color(55, 65, 81), 1, true),
                new EmptyBorder(12, 18, 12, 18)
        ));

        // Título e info
        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        infoPanel.setOpaque(false);

        JLabel lblTitulo = new JLabel("SYSTEM — APLICACIÓN 1: GESTOR Y GENERADOR DE PROCESOS");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitulo.setForeground(new Color(45, 212, 191)); // Teal brillante

        JLabel lblSub = new JLabel("Universidad de Córdoba — Sistemas Operativos 2026-II | REST XML: http://localhost:8080/api/catalogos/xml");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(209, 213, 219));

        infoPanel.add(lblTitulo);
        infoPanel.add(lblSub);
        header.add(infoPanel, BorderLayout.WEST);

        // Botones de acceso rápido en el header con texto y contraste 100% visible
        JPanel botonesHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        botonesHeader.setOpaque(false);

        JButton btnXml = new JButton("🌐 Ver REST XML");
        estilarBotonHeader(btnXml, new Color(59, 130, 246), Color.WHITE);
        btnXml.addActionListener(e -> abrirEnlace("http://localhost:8080/api/catalogos/xml"));

        JButton btnWeb = new JButton("⚡ Abrir Simulador Web");
        estilarBotonHeader(btnWeb, new Color(16, 185, 129), Color.WHITE);
        btnWeb.addActionListener(e -> abrirEnlace("http://localhost:8080"));

        botonesHeader.add(btnXml);
        botonesHeader.add(btnWeb);
        header.add(botonesHeader, BorderLayout.EAST);

        return header;
    }

    private JPanel crearPanelCaptura() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        // ==================== SECCIÓN PASO 1: CAPTURA ====================
        JPanel panelPaso1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        panelPaso1.setBackground(Color.WHITE);
        panelPaso1.setBorder(new CompoundBorder(
                BorderFactory.createTitledBorder(
                        new LineBorder(new Color(209, 213, 219), 1, true),
                        "PASO 1: Captura de procesos reales del Sistema Operativo",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 12),
                        new Color(30, 58, 138)
                ),
                new EmptyBorder(4, 8, 4, 8)
        ));

        panelPaso1.add(new JLabel("Cantidad (N):"));
        spinnerCantidad = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        spinnerCantidad.setPreferredSize(new Dimension(65, 28));
        panelPaso1.add(spinnerCantidad);

        panelPaso1.add(new JLabel("Criterio:"));
        comboCriterio = new JComboBox<>(new String[]{"CPU", "MEMORIA"});
        comboCriterio.setPreferredSize(new Dimension(110, 28));
        panelPaso1.add(comboCriterio);

        btnCapturar = new JButton("⚡ 1. Capturar Procesos del SO");
        estilarBotonAccion(btnCapturar, new Color(37, 99, 235), Color.WHITE);
        btnCapturar.addActionListener(e -> ejecutarCapturaProcesos());
        panelPaso1.add(btnCapturar);

        lblResumenProcesos = new JLabel("Ningún proceso capturado aún.");
        lblResumenProcesos.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblResumenProcesos.setForeground(new Color(75, 85, 99));
        panelPaso1.add(lblResumenProcesos);

        panel.add(panelPaso1, BorderLayout.NORTH);

        // ==================== TABLA CENTRAL ====================
        String[] columnas = {"PID", "Nombre", "Usuario", "Descripción", "Prioridad (P.R)", "CPU %", "Memoria (MB)"};
        tablaProcesosModel = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaProcesos = new JTable(tablaProcesosModel);
        configurarTabla(tablaProcesos);

        JScrollPane scrollTabla = new JScrollPane(tablaProcesos);
        scrollTabla.setBorder(new LineBorder(new Color(229, 231, 235), 1));
        panel.add(scrollTabla, BorderLayout.CENTER);

        // ==================== SECCIÓN PASO 2 Y 3: CATALOGAR Y ACTIVIDAD ====================
        JPanel panelInferior = new JPanel(new GridLayout(2, 1, 8, 8));
        panelInferior.setOpaque(false);

        // PASO 2: Catalogación
        JPanel panelPaso2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        panelPaso2.setBackground(Color.WHITE);
        panelPaso2.setBorder(new CompoundBorder(
                BorderFactory.createTitledBorder(
                        new LineBorder(new Color(209, 213, 219), 1, true),
                        "PASO 2: Catalogar conjunto obtenido y guardar en Base de Datos (H2)",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 12),
                        new Color(22, 101, 52)
                ),
                new EmptyBorder(4, 8, 4, 8)
        ));

        panelPaso2.add(new JLabel("N° Consecutivo Catálogo:"));
        txtNumeroCatalogo = new JTextField("1", 5);
        txtNumeroCatalogo.setPreferredSize(new Dimension(55, 28));
        txtNumeroCatalogo.setToolTipText("Número que identifica este catálogo");
        panelPaso2.add(txtNumeroCatalogo);

        panelPaso2.add(new JLabel("Nombre Descriptivo del Catálogo:"));
        txtNombreCatalogo = new JTextField("Lote de Procesos 1", 22);
        txtNombreCatalogo.setPreferredSize(new Dimension(220, 28));
        txtNombreCatalogo.setToolTipText("Descripción o nombre para hacer referencia a todos los procesos del catálogo");
        panelPaso2.add(txtNombreCatalogo);

        btnGuardarBD = new JButton("💾 2. Guardar Catálogo en BD");
        estilarBotonAccion(btnGuardarBD, new Color(22, 163, 74), Color.WHITE);
        btnGuardarBD.addActionListener(e -> guardarCatalogoEnBD());
        panelPaso2.add(btnGuardarBD);

        JLabel lblAyuda = new JLabel("💡 Agrupa los procesos de la tabla para usarlos en el Simulador Web.");
        lblAyuda.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblAyuda.setForeground(new Color(107, 114, 128));
        panelPaso2.add(lblAyuda);

        panelInferior.add(panelPaso2);

        // PASO 3: Actividad de procesos (Hilos + Archivos TXT)
        JPanel panelPaso3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        panelPaso3.setBackground(Color.WHITE);
        panelPaso3.setBorder(new CompoundBorder(
                BorderFactory.createTitledBorder(
                        new LineBorder(new Color(209, 213, 219), 1, true),
                        "PASO 3: Actividad de Procesos (Hilos independientes Thread + Archivos TXT)",
                        TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 12),
                        new Color(154, 52, 18)
                ),
                new EmptyBorder(4, 8, 4, 8)
        ));

        panelPaso3.add(new JLabel("TH (ms por carácter):"));
        spinnerTH = new JSpinner(new SpinnerNumberModel(50, 5, 2000, 10));
        spinnerTH.setPreferredSize(new Dimension(65, 28));
        panelPaso3.add(spinnerTH);

        btnEjecutarHilos = new JButton("🧵 3. Ejecutar Hilos (Crear Archivos TXT)");
        estilarBotonAccion(btnEjecutarHilos, new Color(234, 88, 12), Color.WHITE);
        btnEjecutarHilos.addActionListener(e -> ejecutarActividadHilos());
        panelPaso3.add(btnEjecutarHilos);

        JButton btnAbrirCarpeta = new JButton("📂 Abrir Carpeta con Archivos Generados");
        estilarBotonAccion(btnAbrirCarpeta, new Color(75, 85, 99), Color.WHITE);
        btnAbrirCarpeta.addActionListener(e -> abrirCarpetaSalida());
        panelPaso3.add(btnAbrirCarpeta);

        panelInferior.add(panelPaso3);

        panel.add(panelInferior, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel crearPanelCatalogosBD() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        // Toolbar de catálogos
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        toolbar.setBackground(Color.WHITE);
        toolbar.setBorder(new LineBorder(new Color(209, 213, 219), 1, true));

        JButton btnRefrescar = new JButton("🔄 Actualizar Lista");
        estilarBotonAccion(btnRefrescar, new Color(37, 99, 235), Color.WHITE);
        btnRefrescar.addActionListener(e -> cargarCatalogosGuardados());
        toolbar.add(btnRefrescar);

        JButton btnVerProcesos = new JButton("👁️ Cargar Procesos en Pantalla");
        estilarBotonAccion(btnVerProcesos, new Color(22, 163, 74), Color.WHITE);
        btnVerProcesos.addActionListener(e -> cargarProcesosDeCatalogoSeleccionado());
        toolbar.add(btnVerProcesos);

        JButton btnVerXmlCat = new JButton("🌐 Ver XML de este Catálogo");
        estilarBotonAccion(btnVerXmlCat, new Color(147, 51, 234), Color.WHITE);
        btnVerXmlCat.addActionListener(e -> abrirXmlCatalogoSeleccionado());
        toolbar.add(btnVerXmlCat);

        JButton btnEliminar = new JButton("🗑️ Eliminar Catálogo");
        estilarBotonAccion(btnEliminar, new Color(220, 38, 38), Color.WHITE);
        btnEliminar.addActionListener(e -> eliminarCatalogoSeleccionado());
        toolbar.add(btnEliminar);

        panel.add(toolbar, BorderLayout.NORTH);

        // Tabla de catálogos
        String[] cols = {"ID", "N° Catálogo", "Nombre del Catálogo", "Criterio", "Fecha Creación", "N° Procesos"};
        tablaCatalogosModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        tablaCatalogos = new JTable(tablaCatalogosModel);
        configurarTabla(tablaCatalogos);

        JScrollPane scroll = new JScrollPane(tablaCatalogos);
        scroll.setBorder(new LineBorder(new Color(229, 231, 235), 1));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel crearPanelLogs() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setPreferredSize(new Dimension(100, 130));
        panel.setOpaque(false);

        JLabel lblLog = new JLabel("Consola de Eventos y Registro del Sistema:");
        lblLog.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLog.setForeground(new Color(55, 65, 81));
        panel.add(lblLog, BorderLayout.NORTH);

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(17, 24, 39));
        txtLogs.setForeground(new Color(229, 231, 235));
        txtLogs.setCaretColor(Color.WHITE);

        JScrollPane scroll = new JScrollPane(txtLogs);
        scroll.setBorder(new LineBorder(new Color(55, 65, 81), 1));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ==================== LÓGICA DE NEGOCIO ====================

    private void ejecutarCapturaProcesos() {
        int cantidad = (Integer) spinnerCantidad.getValue();
        String criterio = (String) comboCriterio.getSelectedItem();

        btnCapturar.setEnabled(false);
        btnCapturar.setText("Capturando...");
        lblResumenProcesos.setText("Obteniendo procesos del SO vía PowerShell...");
        log("Iniciando captura de " + cantidad + " procesos con criterio " + criterio + "...");

        SwingWorker<List<Proceso>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Proceso> doInBackground() {
                return soService.obtenerProcesos(cantidad, criterio);
            }

            @Override
            protected void done() {
                try {
                    procesosCapturados = get();
                    actualizarTablaProcesos(procesosCapturados);

                    long noExp = procesosCapturados.stream().filter(p -> p.getPrioridad() == 1).count();
                    long exp = procesosCapturados.size() - noExp;

                    lblResumenProcesos.setText(String.format("Total: %d procesos | No Expulsivos (SO): %d | Expulsivos: %d",
                            procesosCapturados.size(), noExp, exp));

                    log(String.format("✔ Captura exitosa: %d procesos obtenidos (%d No Expulsivos [SO], %d Expulsivos).",
                            procesosCapturados.size(), noExp, exp));

                } catch (Exception e) {
                    log("❌ Error en captura de procesos: " + e.getMessage());
                    JOptionPane.showMessageDialog(AppEscritorioGUI.this,
                            "Error al capturar procesos: " + e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnCapturar.setEnabled(true);
                    btnCapturar.setText("⚡ 1. Capturar Procesos del SO");
                }
            }
        };

        worker.execute();
    }

    private void actualizarTablaProcesos(List<Proceso> lista) {
        tablaProcesosModel.setRowCount(0);
        for (Proceso p : lista) {
            String prioridadStr = (p.getPrioridad() != null && p.getPrioridad() == 1)
                    ? "1 (No Expulsivo - SO)"
                    : "0 (Expulsivo)";

            tablaProcesosModel.addRow(new Object[]{
                    p.getPid(),
                    p.getNombre(),
                    p.getUsuario(),
                    p.getDescripcion(),
                    prioridadStr,
                    p.getUsoCpu() != null ? String.format("%.2f", p.getUsoCpu()) : "0.00",
                    p.getUsoMemoria() != null ? String.format("%.2f", p.getUsoMemoria()) : "0.00"
            });
        }
    }

    private void guardarCatalogoEnBD() {
        if (procesosCapturados.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Primero debes capturar procesos (Paso 1) antes de guardar el catálogo.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String nombre = txtNombreCatalogo.getText().trim();
        if (nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Por favor escribe un nombre descriptivo para el catálogo.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int numero;
        try {
            numero = Integer.parseInt(txtNumeroCatalogo.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "El número de catálogo debe ser un entero.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String criterio = (String) comboCriterio.getSelectedItem();

        try {
            Catalogo guardado = catalogoService.guardarCatalogoConProcesos(numero, nombre, criterio, procesosCapturados);
            log(String.format("✔ Catálogo #%d '%s' guardado en BD H2 con %d procesos.",
                    guardado.getNumeroCatalogo(), guardado.getNombreCatalogo(), guardado.getProcesos().size()));

            JOptionPane.showMessageDialog(this,
                    "¡Catálogo #" + guardado.getNumeroCatalogo() + " '" + guardado.getNombreCatalogo() + "' guardado exitosamente en BD!\n" +
                    "La aplicación Web ya puede consumirlo inmediatamente.",
                    "Éxito", JOptionPane.INFORMATION_MESSAGE);

            txtNumeroCatalogo.setText(String.valueOf(guardado.getNumeroCatalogo() + 1));
            txtNombreCatalogo.setText("Lote de Procesos " + (guardado.getNumeroCatalogo() + 1));
            cargarCatalogosGuardados();

        } catch (Exception e) {
            log("❌ Error al guardar en BD: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error al guardar en base de datos: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ejecutarActividadHilos() {
        if (procesosCapturados.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No hay procesos capturados para ejecutar la actividad.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int th = (Integer) spinnerTH.getValue();
        log(String.format("Iniciando actividad para %d procesos en hilos independientes (TH = %d ms)...",
                procesosCapturados.size(), th));

        catalogoService.ejecutarActividadEnHilos(procesosCapturados, th, this::log);

        JOptionPane.showMessageDialog(this,
                "Se han iniciado los hilos de ejecución para cada proceso.\n" +
                "Cada hilo crea su archivo en la carpeta 'process-output' escribiendo su descripción carácter por carácter (esperando " + th + " ms).",
                "Hilos en Ejecución", JOptionPane.INFORMATION_MESSAGE);
    }

    private void cargarCatalogosGuardados() {
        tablaCatalogosModel.setRowCount(0);
        List<Catalogo> catalogos = catalogoService.obtenerTodosCatalogos();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (Catalogo c : catalogos) {
            String fecha = c.getFechaCreacion() != null ? c.getFechaCreacion().format(dtf) : "-";
            int count = c.getProcesos() != null ? c.getProcesos().size() : 0;
            tablaCatalogosModel.addRow(new Object[]{
                    c.getId(),
                    c.getNumeroCatalogo(),
                    c.getNombreCatalogo(),
                    c.getCriterioSeleccion(),
                    fecha,
                    count
            });
        }
    }

    private void cargarProcesosDeCatalogoSeleccionado() {
        int fila = tablaCatalogos.getSelectedRow();
        if (fila == -1) {
            JOptionPane.showMessageDialog(this, "Selecciona un catálogo de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long id = (Long) tablaCatalogosModel.getValueAt(fila, 0);
        List<Proceso> procesos = catalogoService.obtenerProcesosDeCatalogo(id);
        procesosCapturados = procesos;
        actualizarTablaProcesos(procesos);

        log("Cargados " + procesos.size() + " procesos del catálogo ID " + id + ".");
        JOptionPane.showMessageDialog(this,
                "Se cargaron " + procesos.size() + " procesos en la pestaña principal.",
                "Carga Exitosa", JOptionPane.INFORMATION_MESSAGE);
    }

    private void abrirXmlCatalogoSeleccionado() {
        int fila = tablaCatalogos.getSelectedRow();
        if (fila == -1) {
            JOptionPane.showMessageDialog(this, "Selecciona un catálogo de la tabla para ver su XML.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long id = (Long) tablaCatalogosModel.getValueAt(fila, 0);
        abrirEnlace("http://localhost:8080/api/catalogos/" + id + "/xml");
    }

    private void eliminarCatalogoSeleccionado() {
        int fila = tablaCatalogos.getSelectedRow();
        if (fila == -1) {
            JOptionPane.showMessageDialog(this, "Selecciona un catálogo para eliminar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long id = (Long) tablaCatalogosModel.getValueAt(fila, 0);
        int resp = JOptionPane.showConfirmDialog(this,
                "¿Deseas eliminar el catálogo seleccionado de la base de datos?",
                "Confirmar Eliminación", JOptionPane.YES_NO_OPTION);

        if (resp == JOptionPane.YES_OPTION) {
            catalogoService.eliminarCatalogo(id);
            log("Catálogo ID " + id + " eliminado de la BD.");
            cargarCatalogosGuardados();
        }
    }

    private void abrirCarpetaSalida() {
        try {
            File dir = new File(catalogoService.getOutputDir());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            Desktop.getDesktop().open(dir);
            log("Abierta carpeta de salida: " + dir.getAbsolutePath());
        } catch (Exception e) {
            log("No se pudo abrir la carpeta: " + e.getMessage());
        }
    }

    private void abrirEnlace(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
            log("Navegador abierto en: " + url);
        } catch (Exception e) {
            log("Error al abrir navegador: " + e.getMessage());
        }
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            txtLogs.append("[" + time + "] " + msg + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    // ==================== ESTILOS Y HELPERS ====================

    private void configurarTabla(JTable tabla) {
        tabla.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabla.setRowHeight(26);
        tabla.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabla.getTableHeader().setBackground(new Color(243, 244, 246));
        tabla.getTableHeader().setForeground(new Color(31, 41, 55));
        tabla.setSelectionBackground(new Color(219, 234, 254));
        tabla.setSelectionForeground(Color.BLACK);
        tabla.setShowVerticalLines(false);
        tabla.setGridColor(new Color(229, 231, 235));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(JLabel.CENTER);

        DefaultTableCellRenderer prioridadRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(JLabel.CENTER);
                String val = value != null ? value.toString() : "";
                if (val.contains("1")) {
                    setForeground(new Color(22, 101, 52)); // Verde oscuro
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setForeground(new Color(30, 64, 175)); // Azul oscuro
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                return c;
            }
        };

        for (int i = 0; i < tabla.getColumnCount(); i++) {
            if (i == 4) {
                tabla.getColumnModel().getColumn(i).setCellRenderer(prioridadRenderer);
            } else if (i == 0 || i == 5 || i == 6) {
                tabla.getColumnModel().getColumn(i).setCellRenderer(center);
            }
        }
    }

    private void estilarBotonAccion(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("JButton.buttonType", "roundRect");
    }

    private void estilarBotonHeader(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("JButton.buttonType", "roundRect");
    }
}
