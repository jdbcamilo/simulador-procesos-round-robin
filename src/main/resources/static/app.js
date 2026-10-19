const API_URL = 'http://localhost:8080/api';

// ==================== ESTADO GLOBAL ====================
let isSimulating = false;
let isPaused = false;
let worker = null;
let simTime = 0;
let quantumTH = 100;
let tickAcumuladoParaQuantum = 0;

let procesosSimulacion = [];
let procesosListos = [];
let procesoEnEjecucion = null;
let procesosEspera = [];
let procesosTerminados = [];

let reporte = [];
let ganttData = [];
let currentGanttBlock = null;
let turnaroundChart = null;

// Sincronización con el escritorio
let knownCatalogIds = new Set();
let syncInterval = null;

// ==================== INICIALIZACIÓN ====================
document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    loadCatalogos();
    iniciarAutoSincronizacion();

    // Listeners de controles principales
    document.getElementById('btn-iniciar-sim').addEventListener('click', handleIniciarSim);
    document.getElementById('btn-pausar-sim').addEventListener('click', handlePausarSim);
    document.getElementById('btn-detener-sim').addEventListener('click', handleDetenerSim);
    document.getElementById('btn-sync-catalogos').addEventListener('click', () => {
        loadCatalogos(true);
        showToast('Catálogos sincronizados con el escritorio', 'info');
    });

    // Visor XML
    document.getElementById('btn-cargar-xml-visor')?.addEventListener('click', cargarXmlVisor);
    document.getElementById('btn-copiar-xml')?.addEventListener('click', copiarXml);

    // Pestaña Catálogos
    document.getElementById('btn-refresh-catalogos')?.addEventListener('click', loadCatalogos);
    document.getElementById('btn-cerrar-detalle')?.addEventListener('click', () => {
        document.getElementById('catalogo-detalle')?.classList.add('hidden');
    });
});

// ==================== NAVEGACIÓN ====================
function initNavigation() {
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const targetTab = btn.dataset.tab;
            if (!targetTab) return; // Enlace directo a XML puro

            e.preventDefault();
            document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
            document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));

            btn.classList.add('active');
            const sec = document.getElementById(`tab-${targetTab}`);
            if (sec) sec.classList.add('active');

            if (targetTab === 'reportes') {
                generarReportes();
            } else if (targetTab === 'xml') {
                cargarXmlVisor();
            }
        });
    });
}

// ==================== SINCRONIZACIÓN EN TIEMPO REAL ====================
function iniciarAutoSincronizacion() {
    if (syncInterval) clearInterval(syncInterval);
    // Cada 3 segundos sondea nuevos catálogos si no está en simulación activa
    syncInterval = setInterval(() => {
        if (!isSimulating) {
            verificarNuevosCatalogos();
        }
    }, 3000);
}

async function verificarNuevosCatalogos() {
    try {
        const res = await fetch(`${API_URL}/catalogos`);
        if (!res.ok) return;
        const catalogos = await res.json();
        
        let hayNuevos = false;
        catalogos.forEach(c => {
            if (!knownCatalogIds.has(c.id)) {
                hayNuevos = true;
            }
        });

        if (hayNuevos && knownCatalogIds.size > 0) {
            loadCatalogos(false);
            showToast('✨ Nuevo catálogo detectado desde la App de Escritorio', 'success');
        }
    } catch (e) {
        // Silencioso en sondeo
    }
}

async function loadCatalogos(notificar = false) {
    try {
        const res = await fetch(`${API_URL}/catalogos`);
        if (!res.ok) throw new Error('Error al conectar con la API');
        const catalogos = await res.json();

        const select = document.getElementById('sim-catalogo');
        const list = document.getElementById('catalogos-list');

        const prevValue = select.value;
        select.innerHTML = '<option value="">-- Selecciona un catálogo para simular --</option>';
        if (list) list.innerHTML = '';

        knownCatalogIds.clear();

        if (!catalogos || catalogos.length === 0) {
            select.innerHTML = '<option value="">No hay catálogos aún. Crea uno desde la App de Escritorio</option>';
            if (list) list.innerHTML = '<div style="text-align:center; padding:2rem; color:var(--text-muted);">No hay catálogos guardados en la BD.</div>';
            return;
        }

        catalogos.forEach(c => {
            knownCatalogIds.add(c.id);
            const nombreMostrar = c.nombreCatalogo || ('Catálogo #' + c.numeroCatalogo);
            const numProc = c.procesos ? c.procesos.length : 0;

            const opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = `Catálogo #${c.numeroCatalogo}: ${nombreMostrar} (${numProc} procesos) [${c.criterioSeleccion || 'CPU'}]`;
            select.appendChild(opt);

            if (list) {
                const item = document.createElement('div');
                item.className = 'catalog-item';
                item.innerHTML = `
                    <div>
                        <h4 style="color:#ffffff; font-size:0.95rem; margin-bottom:0.2rem;">
                            #${c.numeroCatalogo} — ${nombreMostrar}
                        </h4>
                        <div style="font-size:0.75rem; color:var(--text-secondary);">
                            Criterio: <strong style="color:#38bdf8;">${c.criterioSeleccion || 'CPU'}</strong> | 
                            Procesos: <strong style="color:#34d399;">${numProc}</strong> | 
                            Fecha: ${c.fechaCreacion || '-'}
                        </div>
                    </div>
                    <div style="display:flex; gap:0.5rem;">
                        <button class="btn btn-secondary btn-sm" onclick="verDetalle(${c.id})">👁️ Ver</button>
                        <a href="/api/catalogos/${c.id}/xml" target="_blank" class="btn btn-secondary btn-sm" style="text-decoration:none;">XML</a>
                        <button class="btn btn-danger btn-sm" onclick="eliminarCatalogo(${c.id})">Borrar</button>
                    </div>
                `;
                list.appendChild(item);
            }
        });

        if (prevValue && knownCatalogIds.has(Number(prevValue))) {
            select.value = prevValue;
        } else if (catalogos.length > 0 && !select.value) {
            select.selectedIndex = 1; // Seleccionar el primero por defecto
        }

        if (notificar) {
            showToast(`Cargados ${catalogos.length} catálogos`, 'success');
        }
    } catch (e) {
        console.error(e);
        showToast('Error cargando catálogos desde el servidor', 'error');
    }
}

window.verDetalle = async function(id) {
    try {
        const res = await fetch(`${API_URL}/catalogos/${id}/procesos`);
        const procesos = await res.json();
        const tbody = document.getElementById('tabla-procesos-body');
        if (!tbody) return;
        tbody.innerHTML = '';

        procesos.forEach(p => {
            const esNoExp = p.prioridad === 1;
            tbody.innerHTML += `
                <tr>
                    <td><code>${p.pid}</code></td>
                    <td><strong>${p.nombre}</strong></td>
                    <td>${p.usuario || 'SYSTEM'}</td>
                    <td title="${p.descripcion || ''}">${(p.descripcion || '-').substring(0, 45)}...</td>
                    <td><span class="badge ${esNoExp ? 'badge-no-exp' : 'badge-exp'}">${esNoExp ? '1 (No Expulsivo - SO)' : '0 (Expulsivo)'}</span></td>
                    <td>${p.usoCpu != null ? Number(p.usoCpu).toFixed(2) : '0.00'} %</td>
                    <td>${p.usoMemoria != null ? Number(p.usoMemoria).toFixed(2) : '0.00'} MB</td>
                </tr>
            `;
        });
        document.getElementById('catalogo-detalle')?.classList.remove('hidden');
    } catch (e) {
        showToast('Error cargando detalle de procesos', 'error');
    }
};

window.eliminarCatalogo = async function(id) {
    if (!confirm('¿Seguro que deseas eliminar este catálogo de la base de datos?')) return;
    try {
        await fetch(`${API_URL}/catalogos/${id}`, { method: 'DELETE' });
        showToast('Catálogo eliminado', 'success');
        loadCatalogos();
    } catch (e) {
        showToast('Error al eliminar', 'error');
    }
};

// ==================== SIMULADOR ROUND ROBIN ====================
async function handleIniciarSim() {
    const catalogoId = document.getElementById('sim-catalogo').value;
    quantumTH = parseInt(document.getElementById('sim-quantum').value) || 100;

    if (!catalogoId) {
        showToast('Por favor selecciona un catálogo para simular', 'error');
        return;
    }

    if (isSimulating && isPaused) {
        isPaused = false;
        worker.postMessage({ type: 'RESUME' });
        updateSimControls(true, false);
        logSim('Simulación reanudada.');
        return;
    }

    try {
        const res = await fetch(`${API_URL}/catalogos/${catalogoId}/procesos`);
        procesosSimulacion = await res.json();

        if (!procesosSimulacion || procesosSimulacion.length === 0) {
            showToast('El catálogo seleccionado no tiene procesos', 'error');
            return;
        }

        // Ejecutar los hilos reales en el backend para crear los archivos físicos de texto
        fetch(`${API_URL}/catalogos/${catalogoId}/ejecutar-actividad?th=${quantumTH}`, { method: 'POST' })
            .then(r => r.json())
            .then(data => {
                logSim(`[HILOS JAVA] Hilos independientes iniciados en backend (${data.directorio})`);
            })
            .catch(err => console.log('Actividad backend:', err));

        iniciarSimulacion(procesosSimulacion);
    } catch (e) {
        showToast('Error al obtener procesos del catálogo', 'error');
    }
}

function handlePausarSim() {
    if (isSimulating && !isPaused) {
        isPaused = true;
        if (worker) worker.postMessage({ type: 'PAUSE' });
        updateSimControls(true, true);
        logSim('Simulación pausada.');
    }
}

function handleDetenerSim() {
    detenerSimulacion();
    logSim('Simulación detenida por el usuario.');
    showToast('Simulación detenida', 'info');
}

function iniciarSimulacion(procesos) {
    isSimulating = true;
    isPaused = false;
    simTime = 0;
    tickAcumuladoParaQuantum = 0;
    reporte = [];
    procesosListos = [];
    procesosEspera = [];
    procesosTerminados = [];
    procesoEnEjecucion = null;
    ganttData = [];
    currentGanttBlock = null;

    document.getElementById('sim-log').innerHTML = '';
    document.getElementById('gantt-container').innerHTML = '';
    logSim(`⚡ Iniciando Simulación Round Robin con Quantum (TH) = ${quantumTH} ms`);
    updateSimControls(true, false);

    // Preparar cola de listos con cálculo de tiempos de llegada y ráfaga
    let tlAccum = 0;
    procesosListos = procesos.map((p, index) => {
        let descLen = p.descripcion ? p.descripcion.length : 12;
        if (descLen === 0) descLen = 12;

        const tl = index === 0 ? 0 : tlAccum + 1;
        tlAccum = tl;

        const rafagaTotal = quantumTH * descLen;

        return {
            id: p.id,
            pid: p.pid,
            nombre: p.nombre,
            descripcion: p.descripcion,
            prioridad: p.prioridad, // 0 = Expulsivo, 1 = No Expulsivo (SO)
            rafaga: rafagaTotal,
            rafagaRestante: rafagaTotal,
            tiempoLlegada: tl,
            ejecuciones: 0,
            tiempoFinalizacion: null,
            caracteresTotales: descLen
        };
    });

    logSim(`Cargados ${procesosListos.length} procesos a la cola de LISTOS.`);
    renderQueues();

    if (worker) worker.terminate();

    const workerCode = `
        let isPaused = false;
        let timer = null;

        self.onmessage = function(e) {
            if (e.data.type === 'START') {
                isPaused = false;
                timer = setInterval(() => {
                    if (!isPaused) {
                        self.postMessage({ type: 'TICK' });
                    }
                }, 80); 
            } else if (e.data.type === 'PAUSE') {
                isPaused = true;
            } else if (e.data.type === 'RESUME') {
                isPaused = false;
            } else if (e.data.type === 'STOP') {
                clearInterval(timer);
                self.close();
            }
        };
    `;

    const blob = new Blob([workerCode], { type: 'application/javascript' });
    worker = new Worker(URL.createObjectURL(blob));
    worker.onmessage = handleWorkerTick;
    worker.postMessage({ type: 'START' });
}

function handleWorkerTick(e) {
    if (e.data.type !== 'TICK') return;

    const avance = Math.max(1, quantumTH / 10);
    simTime += avance;
    document.getElementById('sim-time').textContent = Math.floor(simTime) + ' ms';
    document.getElementById('stat-total-time').textContent = Math.floor(simTime) + ' ms';

    // Condición de parada: todo terminado
    if (procesosListos.length === 0 && !procesoEnEjecucion && procesosEspera.length === 0) {
        logSim('✔ Simulación completada: Todos los procesos han finalizado su ciclo de vida.');
        detenerSimulacion();
        generarReportes();
        showToast('¡Simulación Round Robin completada con éxito!', 'success');
        return;
    }

    // Si la CPU está libre, tomar el siguiente de LISTOS
    if (!procesoEnEjecucion) {
        if (procesosListos.length > 0) {
            procesoEnEjecucion = procesosListos.shift();
            procesoEnEjecucion.ejecuciones++;
            tickAcumuladoParaQuantum = 0;

            currentGanttBlock = {
                id: procesoEnEjecucion.id,
                name: procesoEnEjecucion.nombre,
                start: simTime,
                end: null
            };

            const tipo = procesoEnEjecucion.prioridad === 1 ? 'No Expulsivo (SO)' : 'Expulsivo';
            logSim(`[EXEC] ${procesoEnEjecucion.nombre} (PID: ${procesoEnEjecucion.pid}, ${tipo}) ingresa a CPU.`);
            renderQueues();
        }
    } else {
        // Ejecución activa del proceso en CPU
        procesoEnEjecucion.rafagaRestante -= avance;
        tickAcumuladoParaQuantum += avance;

        // Caso a: Culminó su actividad en totalidad
        if (procesoEnEjecucion.rafagaRestante <= 0) {
            procesoEnEjecucion.rafagaRestante = 0;
            procesoEnEjecucion.tiempoFinalizacion = simTime;

            logSim(`[TERM] ${procesoEnEjecucion.nombre} completó todos sus caracteres -> TERMINADO.`);
            procesosTerminados.push(procesoEnEjecucion);
            reporte.push({ ...procesoEnEjecucion });

            if (currentGanttBlock) {
                currentGanttBlock.end = simTime;
                ganttData.push(currentGanttBlock);
                currentGanttBlock = null;
                renderGantt();
            }

            procesoEnEjecucion = null;
            tickAcumuladoParaQuantum = 0;
            renderQueues();
            generarReportes();
            return;
        }

        // Caso b: Proceso expulsivo agota su quantum TH
        if (procesoEnEjecucion.prioridad === 0 && tickAcumuladoParaQuantum >= quantumTH) {
            logSim(`[EXPULSIÓN] ${procesoEnEjecucion.nombre} agotó quantum (${quantumTH}ms) -> Vuelve a LISTOS.`);
            procesosListos.push(procesoEnEjecucion);

            if (currentGanttBlock) {
                currentGanttBlock.end = simTime;
                ganttData.push(currentGanttBlock);
                currentGanttBlock = null;
                renderGantt();
            }

            procesoEnEjecucion = null;
            tickAcumuladoParaQuantum = 0;
            renderQueues();
        } else if (procesoEnEjecucion.prioridad === 1) {
            // Caso No Expulsivo (SO): No sale de CPU por quantum, pero cuenta cada bloque ejecutado
            if (tickAcumuladoParaQuantum >= quantumTH) {
                tickAcumuladoParaQuantum = 0;
                procesoEnEjecucion.ejecuciones++;
                renderQueues();
            }
        }
    }

    renderQueues();
}

function detenerSimulacion() {
    isSimulating = false;
    isPaused = false;
    if (worker) {
        worker.postMessage({ type: 'STOP' });
        worker.terminate();
        worker = null;
    }
    updateSimControls(false, false);
    generarReportes();
}

function updateSimControls(running, paused) {
    const btnIn = document.getElementById('btn-iniciar-sim');
    const btnPa = document.getElementById('btn-pausar-sim');
    const btnDe = document.getElementById('btn-detener-sim');

    if (!running) {
        btnIn.disabled = false;
        btnIn.innerHTML = '<span>▶</span> Iniciar';
        btnPa.disabled = true;
        btnDe.disabled = true;
    } else if (paused) {
        btnIn.disabled = false;
        btnIn.innerHTML = '<span>▶</span> Reanudar';
        btnPa.disabled = true;
        btnDe.disabled = false;
    } else {
        btnIn.disabled = true;
        btnPa.disabled = false;
        btnDe.disabled = false;
    }
}

// ==================== ZONA DE ESPERA MANUAL ====================
window.mandarAEspera = function(id) {
    if (procesoEnEjecucion && procesoEnEjecucion.id === id) {
        logSim(`[ESPERA] ${procesoEnEjecucion.nombre} enviado manualmente a zona de ESPERA.`);
        procesosEspera.push(procesoEnEjecucion);

        if (currentGanttBlock) {
            currentGanttBlock.end = simTime;
            ganttData.push(currentGanttBlock);
            currentGanttBlock = null;
            renderGantt();
        }

        procesoEnEjecucion = null;
        tickAcumuladoParaQuantum = 0;
        renderQueues();
    }
};

window.reanudarDeEspera = function(id) {
    const idx = procesosEspera.findIndex(p => p.id === id);
    if (idx !== -1) {
        const p = procesosEspera.splice(idx, 1)[0];
        logSim(`[REANUDAR] ${p.nombre} sale de ESPERA y regresa a LISTOS.`);
        procesosListos.push(p);
        renderQueues();
    }
};

// ==================== RENDERIZADO DE COLAS Y TARJETAS ====================
function renderQueues() {
    const qListo = document.getElementById('queue-listo');
    const qEjecucion = document.getElementById('queue-ejecucion');
    const qEspera = document.getElementById('queue-espera');
    const qTerminado = document.getElementById('queue-terminado');

    if (qListo) qListo.innerHTML = procesosListos.map(p => createProcBoxHtml(p, 'listo')).join('');
    if (qEjecucion) qEjecucion.innerHTML = procesoEnEjecucion ? createProcBoxHtml(procesoEnEjecucion, 'ejec') : '<div style="color:var(--text-muted); text-align:center; padding:2rem; font-size:0.82rem;">CPU libre (En espera de proceso)</div>';
    if (qEspera) qEspera.innerHTML = procesosEspera.map(p => createProcBoxHtml(p, 'espera')).join('');
    if (qTerminado) qTerminado.innerHTML = procesosTerminados.map(p => createProcBoxHtml(p, 'term')).join('');

    // Actualizar contadores en encabezados
    document.getElementById('count-listos').textContent = procesosListos.length;
    document.getElementById('count-ejecucion').textContent = procesoEnEjecucion ? 1 : 0;
    document.getElementById('count-espera').textContent = procesosEspera.length;
    document.getElementById('count-terminado').textContent = procesosTerminados.length;
}

function createProcBoxHtml(p, state) {
    const esNoExp = p.prioridad === 1;
    const prioridadBadge = esNoExp
        ? `<span class="badge badge-no-exp">1 • No Expulsivo (SO)</span>`
        : `<span class="badge badge-exp">0 • Expulsivo</span>`;

    const quantumRestante = Math.max(0, Math.ceil(p.rafagaRestante / quantumTH));
    const progresoPct = p.rafaga > 0 ? Math.min(100, Math.round(((p.rafaga - p.rafagaRestante) / p.rafaga) * 100)) : 100;

    let actionButton = '';
    if (state === 'ejec') {
        actionButton = `<button class="btn-card-action btn-action-espera" onclick="mandarAEspera(${p.id})">⏳ Mandar a Espera</button>`;
    } else if (state === 'espera') {
        actionButton = `<button class="btn-card-action btn-action-reanudar" onclick="reanudarDeEspera(${p.id})">⚡ Reanudar a Listos</button>`;
    }

    return `
        <div class="proc-box ${state}">
            <div class="proc-box-header">
                <span class="proc-name" title="${p.nombre}">${p.nombre}</span>
                <span class="proc-pid">PID: ${p.pid}</span>
            </div>

            <div style="margin-bottom:0.5rem;">
                ${prioridadBadge}
            </div>

            <div class="proc-metrics">
                <div class="metric-item">Quantum: <strong>${quantumRestante}</strong></div>
                <div class="metric-item">Ejecs: <strong>${p.ejecuciones}</strong></div>
                <div class="metric-item">Ráfaga: <strong>${Math.floor(p.rafagaRestante)}ms</strong></div>
                <div class="metric-item">Progreso: <strong>${progresoPct}%</strong></div>
            </div>

            <div class="proc-progress-bg">
                <div class="proc-progress-bar" style="width: ${progresoPct}%;"></div>
            </div>

            ${actionButton}
        </div>
    `;
}

// ==================== DIAGRAMA DE GANTT ====================
function renderGantt() {
    const container = document.getElementById('gantt-container');
    if (!container) return;

    if (ganttData.length === 0) {
        container.innerHTML = `<div style="text-align:center; padding:2rem; color:var(--text-muted);">Diagrama en espera de bloques...</div>`;
        return;
    }

    const maxTime = Math.max(simTime, 1);
    const processesMap = {};

    ganttData.forEach(b => {
        if (!processesMap[b.name]) processesMap[b.name] = [];
        processesMap[b.name].push(b);
    });

    let html = `<div class="gantt-chart-v2">`;
    html += `
        <div class="gantt-timeline-header">
            <span>0 ms</span>
            <span>${Math.floor(maxTime * 0.25)} ms</span>
            <span>${Math.floor(maxTime * 0.50)} ms</span>
            <span>${Math.floor(maxTime * 0.75)} ms</span>
            <span>${Math.floor(maxTime)} ms (Fin)</span>
        </div>
    `;

    for (const [pName, blocks] of Object.entries(processesMap)) {
        html += `
            <div class="gantt-row">
                <div class="gantt-label" title="${pName}">${pName}</div>
                <div class="gantt-bars-area">
        `;

        blocks.forEach(b => {
            const blockEnd = b.end || simTime;
            const leftPct = (b.start / maxTime) * 100;
            const widthPct = Math.max(0.6, ((blockEnd - b.start) / maxTime) * 100);

            html += `
                <div class="gantt-bar" 
                     style="left: ${leftPct}%; width: ${widthPct}%;" 
                     title="Proceso: ${pName} | Inicio: ${Math.floor(b.start)}ms | Fin: ${Math.floor(blockEnd)}ms | Duración: ${Math.floor(blockEnd - b.start)}ms">
                </div>
            `;
        });

        html += `
                </div>
            </div>
        `;
    }

    html += `</div>`;
    container.innerHTML = html;
}

// ==================== REPORTES Y GRÁFICAS ====================
function generarReportes() {
    const tbodyGeneral = document.getElementById('reporte-ejecucion-body');
    const tbodyNoExp = document.getElementById('reporte-no-expulsivos-body');

    if (tbodyGeneral) tbodyGeneral.innerHTML = '';
    if (tbodyNoExp) tbodyNoExp.innerHTML = '';

    if (reporte.length === 0) return;

    let totalTurnaround = 0;
    let totalEspera = 0;
    const chartLabels = [];
    const chartData = [];

    reporte.forEach(p => {
        const tf = Math.floor(p.tiempoFinalizacion || (quantumTH * p.ejecuciones));
        const tr = Math.max(p.rafaga, tf - p.tiempoLlegada);
        const te = Math.max(0, tr - p.rafaga);

        totalTurnaround += tr;
        totalEspera += te;

        chartLabels.push(p.nombre);
        chartData.push(tr);

        const esNoExp = p.prioridad === 1;

        if (tbodyGeneral) {
            tbodyGeneral.innerHTML += `
                <tr>
                    <td><strong>${p.nombre}</strong> <code style="color:var(--text-muted); font-size:0.75rem;">(${p.pid})</code></td>
                    <td>${p.tiempoLlegada} ms</td>
                    <td>${p.rafaga} ms</td>
                    <td><span class="badge ${esNoExp ? 'badge-no-exp' : 'badge-exp'}">${esNoExp ? '1 • No Expulsivo (SO)' : '0 • Expulsivo'}</span></td>
                    <td><strong style="color:#00f5d4;">${tr} ms</strong></td>
                    <td>${tf} ms</td>
                    <td>${p.ejecuciones}</td>
                </tr>
            `;
        }

        if (esNoExp && tbodyNoExp) {
            tbodyNoExp.innerHTML += `
                <tr>
                    <td><strong>${p.nombre}</strong> <code style="color:var(--text-muted); font-size:0.75rem;">(${p.pid})</code></td>
                    <td>${p.tiempoLlegada} ms</td>
                    <td>${p.rafaga} ms</td>
                    <td><span class="badge badge-no-exp">1 • No Expulsivo (SO)</span></td>
                    <td><strong style="color:#00f5d4;">${tr} ms</strong></td>
                    <td>${tf} ms</td>
                </tr>
            `;
        }
    });

    // Actualizar tarjetas de métricas
    const n = reporte.length;
    actualizarTexto('stat-total-procesos', n);
    actualizarTexto('stat-avg-turnaround', n > 0 ? Math.floor(totalTurnaround / n) + ' ms' : '0 ms');
    actualizarTexto('stat-avg-espera', n > 0 ? Math.floor(totalEspera / n) + ' ms' : '0 ms');
    actualizarTexto('stat-total-time', Math.floor(simTime) + ' ms');
    actualizarTexto('stat-cpu-util', simTime > 0 ? '100 %' : '0 %');

    if (simTime > 0 && n > 0) {
        const thVal = (n / (simTime / 1000)).toFixed(2);
        actualizarTexto('stat-throughput', `${thVal} p/s`);
    }

    renderizarGrafica(chartLabels, chartData);
}

function renderizarGrafica(labels, data) {
    const ctx = document.getElementById('turnaroundChart');
    if (!ctx) return;

    if (turnaroundChart) {
        turnaroundChart.destroy();
    }

    turnaroundChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [{
                label: 'TurnaRound (T.R en ms)',
                data: data,
                backgroundColor: 'rgba(0, 245, 212, 0.55)',
                borderColor: '#00f5d4',
                borderWidth: 2,
                borderRadius: 6
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    labels: { color: '#ffffff', font: { family: 'Inter', weight: 'bold' } }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: { color: '#9ca3af', font: { family: 'JetBrains Mono' } },
                    grid: { color: 'rgba(255, 255, 255, 0.08)' },
                    title: { display: true, text: 'Tiempo (ms)', color: '#9ca3af' }
                },
                x: {
                    ticks: { color: '#ffffff', font: { family: 'Inter' } },
                    grid: { display: false }
                }
            }
        }
    });
}

// ==================== VISOR REST XML ====================
async function cargarXmlVisor() {
    const codeViewer = document.getElementById('xml-code-viewer');
    const statusText = document.getElementById('xml-load-status');
    if (!codeViewer) return;

    codeViewer.textContent = 'Consultando endpoint GET /api/catalogos/xml...';

    try {
        const res = await fetch(`${API_URL}/catalogos/xml`, {
            headers: { 'Accept': 'application/xml' }
        });

        if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);

        const xmlText = await res.text();
        codeViewer.textContent = xmlText;
        if (statusText) statusText.textContent = `● Activo (${xmlText.length} bytes)`;
    } catch (e) {
        codeViewer.textContent = `Error al cargar XML: ${e.message}\nAsegúrate de que el backend Spring Boot esté en ejecución.`;
        if (statusText) statusText.textContent = '● Error al consultar';
    }
}

function copiarXml() {
    const code = document.getElementById('xml-code-viewer')?.textContent;
    if (code) {
        navigator.clipboard.writeText(code)
            .then(() => showToast('¡XML copiado al portapapeles!', 'success'))
            .catch(() => showToast('No se pudo copiar', 'error'));
    }
}

// ==================== LOGS Y HELPERS ====================
function logSim(msg) {
    const log = document.getElementById('sim-log');
    if (!log) return;
    const entry = document.createElement('div');
    entry.style.marginBottom = '0.3rem';
    entry.textContent = `[${Math.floor(simTime)}ms] ${msg}`;
    log.prepend(entry);
    if (log.children.length > 60) log.lastChild.remove();
}

function actualizarTexto(elementId, text) {
    const el = document.getElementById(elementId);
    if (el) el.textContent = text;
}

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `<span>●</span> ${message}`;

    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}
