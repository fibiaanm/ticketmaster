'use strict';

// Interfaz minima: login, listado, detalle (analisis de IA e historial), alta y metricas.
// Todo el contenido que viene de la API se inserta como TEXTO (nunca como HTML).

const ESTADOS = ['ABIERTO', 'EN_PROCESO', 'RESUELTO', 'CERRADO'];
const CATEGORIAS = ['HARDWARE', 'SOFTWARE', 'CONECTIVIDAD', 'ACCESOS', 'OTRO'];
const PRIORIDADES = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA'];
const TAMANO = 10;
const EVENTOS = {
  TICKET_CREADO: 'Ticket creado', ESTADO_CAMBIADO: 'Estado cambiado', TECNICO_ASIGNADO: 'Técnico asignado',
  TICKET_ELIMINADO: 'Ticket eliminado', ANALISIS_COMPLETADO: 'Análisis de IA completado',
  ANALISIS_FALLIDO: 'Análisis de IA fallido', REANALISIS_SOLICITADO: 'Reanálisis solicitado',
};

let sesion = leerSesion();
let temporizador = null;
const filtros = { q: '', estado: '', categoria: '', prioridad: '', sort: '', page: 0 };

// ---------- utilidades
function leerSesion() {
  try { return JSON.parse(sessionStorage.getItem('sesion')); } catch (e) { return null; }
}

function h(tag, atributos, ...hijos) {
  const el = document.createElement(tag);
  for (const [clave, valor] of Object.entries(atributos || {})) {
    if (valor === null || valor === undefined || valor === false) continue;
    if (clave === 'class') el.className = valor;
    else if (clave.startsWith('on')) el.addEventListener(clave.slice(2), valor);
    else if (clave === 'value') el.value = valor;
    else el.setAttribute(clave, valor === true ? '' : valor);
  }
  for (const hijo of hijos.flat()) {
    if (hijo === null || hijo === undefined || hijo === false) continue;
    el.append(hijo instanceof Node ? hijo : document.createTextNode(String(hijo)));
  }
  return el;
}

/** tipo: e = estado, p = prioridad, a = analisis, c = categoria. */
function badge(texto, tipo) {
  return h('span', { class: 'badge ' + tipo + '-' + texto }, texto.replace('_', ' '));
}

function categoria(t) {
  return t.categoria ? h('span', { class: 'badge' }, t.categoria) : h('span', { class: 'badge c-sin' }, 'sin analizar');
}

function opciones(valores, actual, vacio) {
  const lista = vacio ? [h('option', { value: '' }, vacio)] : [];
  for (const v of valores) lista.push(h('option', { value: v, selected: v === actual }, v.replace('_', ' ')));
  return lista;
}

function fecha(texto) {
  if (!texto) return '';
  return new Date(texto + 'Z').toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }); // la API devuelve UTC sin zona
}

function campo(nombre, control, clase) {
  return h('label', { class: clase }, nombre, control);
}

// ---------- API
async function api(ruta, { metodo = 'GET', cuerpo, consulta, cabeceras } = {}) {
  const url = new URL(ruta, location.origin);
  for (const [k, v] of Object.entries(consulta || {})) {
    if (v !== '' && v !== null && v !== undefined) url.searchParams.set(k, v);
  }
  const headers = Object.assign({ Accept: 'application/json' }, cabeceras);
  if (sesion) headers.Authorization = 'Bearer ' + sesion.accessToken;
  if (cuerpo) headers['Content-Type'] = 'application/json';
  const r = await fetch(url, { method: metodo, headers, body: cuerpo ? JSON.stringify(cuerpo) : undefined });
  if (r.status === 204) return null;
  let datos = null;
  try { datos = await r.json(); } catch (e) { /* sin cuerpo */ }
  if (!r.ok) {
    if (r.status === 401 && sesion) salirLocal();
    const error = new Error(r.status === 429
      ? 'Demasiadas peticiones; reintente en ' + r.headers.get('Retry-After') + ' s'
      : (datos && datos.message) || 'Error ' + r.status);
    error.status = r.status;
    throw error;
  }
  return datos;
}

function salirLocal() {
  sesion = null;
  sessionStorage.removeItem('sesion');
  location.hash = '';
  pintar();
}

async function salir() {
  try { await api('/api/auth/logout', { metodo: 'POST', cabeceras: { 'X-Ticket-Auth': sesion.sessionToken } }); } catch (e) { /* ya expirada */ }
  salirLocal();
}

// ---------- estructura
const contenido = () => document.getElementById('contenido');

function aviso(mensaje) {
  contenido().querySelectorAll('.aviso').forEach((n) => n.remove());
  contenido().prepend(h('div', { class: 'aviso' }, mensaje));
}

function cabecera(pagina) {
  const c = document.getElementById('cabecera');
  c.replaceChildren(h('div', { class: 'marca' }, h('span', { class: 'logo' }, 'T'), 'Gestión de tickets'));
  if (!sesion) return;
  const enlace = (hash, texto, activo) => h('a', { href: hash, class: activo ? 'activo' : '' }, texto);
  c.append(
    h('nav', null,
      enlace('#/tickets', 'Tickets', pagina === 'tickets' || !pagina),
      enlace('#/nuevo', 'Nuevo ticket', pagina === 'nuevo'),
      sesion.rol !== 'USUARIO' ? enlace('#/metricas', 'Métricas', pagina === 'metricas') : null),
    h('a', { class: 'externo', href: '/swagger-ui.html', target: '_blank', rel: 'noopener' }, 'API ↗'),
    h('a', { class: 'externo', href: 'http://localhost:5601/app/discover', target: '_blank', rel: 'noopener' }, 'Logs ↗'),
    h('div', { class: 'usuario' }, h('span', { class: 'avatar' }, sesion.nombre.charAt(0).toUpperCase()), sesion.nombre,
      h('span', { class: 'rol' }, sesion.rol)),
    h('button', { class: 'enlace', onclick: salir }, 'Salir'));
}

// ---------- vistas
function vistaLogin() {
  const correo = h('input', { type: 'email', required: true, value: 'usuario@tickets.local', autocomplete: 'username' });
  const password = h('input', { type: 'password', required: true, value: 'pw', autocomplete: 'current-password' });
  const formulario = h('form', {
    onsubmit: async (e) => {
      e.preventDefault();
      try {
        sesion = await api('/api/auth/login', { metodo: 'POST', cuerpo: { correo: correo.value, password: password.value } });
        sessionStorage.setItem('sesion', JSON.stringify(sesion));
        location.hash = '#/tickets';
        pintar();
      } catch (error) {
        aviso(error.message);
      }
    },
  }, campo('Correo', correo), campo('Contraseña', password), h('button', { class: 'primario' }, 'Entrar'));
  const rapido = (rol) => h('button', { type: 'button', onclick: () => { correo.value = rol + '@tickets.local'; password.value = 'pw'; } }, rol);
  contenido().replaceChildren(h('div', { class: 'tarjeta login' },
    h('div', { class: 'logo' }, 'T'),
    h('h1', { class: 'titulo' }, 'Bienvenido'),
    h('p', { class: 'subtitulo' }, 'Inicia sesión para ver y gestionar los tickets de soporte.'),
    formulario,
    h('div', { class: 'acceso' }, h('span', { class: 'suave' }, 'Acceso rápido (desarrollo):'), rapido('admin'), rapido('soporte'), rapido('usuario'))));
}

async function vistaListado() {
  const q = h('input', { type: 'search', value: filtros.q, placeholder: 'Título o descripción…' });
  const estado = h('select', null, opciones(ESTADOS, filtros.estado, 'Todos'));
  const cat = h('select', null, opciones(CATEGORIAS, filtros.categoria, 'Todas'));
  const prioridad = h('select', null, opciones(PRIORIDADES, filtros.prioridad, 'Todas'));
  const orden = h('select', null,
    h('option', { value: '' }, 'Más recientes'),
    h('option', { value: 'prioridad,desc', selected: filtros.sort === 'prioridad,desc' }, 'Prioridad (alta primero)'),
    h('option', { value: 'titulo,asc', selected: filtros.sort === 'titulo,asc' }, 'Título (A-Z)'));

  const resultado = h('div', { class: 'tarjeta' }, h('div', { class: 'cargando' }, 'Cargando…'));
  const buscar = (e) => {
    if (e) e.preventDefault();
    Object.assign(filtros, { q: q.value, estado: estado.value, categoria: cat.value, prioridad: prioridad.value, sort: orden.value, page: 0 });
    cargar();
  };
  const formulario = h('form', { class: 'fila', onsubmit: buscar },
    campo('Buscar', q, 'ancho'), campo('Estado', estado), campo('Categoría', cat), campo('Prioridad', prioridad),
    campo('Orden', orden), h('button', { class: 'primario' }, 'Buscar'));

  contenido().replaceChildren(
    h('h1', { class: 'titulo' }, 'Tickets'),
    h('p', { class: 'subtitulo' }, sesion.rol === 'USUARIO' ? 'Tus solicitudes de soporte.' : 'Todas las solicitudes de soporte.'),
    h('div', { class: 'tarjeta' }, formulario), resultado);

  async function cargar() {
    try {
      const pagina = await api('/api/tickets', { consulta: { ...filtros, size: TAMANO } });
      const filas = pagina.contenido.map((t) => h('tr', { class: 'clicable', onclick: () => { location.hash = '#/tickets/' + t.id; } },
        h('td', { class: 'id' }, '#' + t.id),
        h('td', { class: 'principal' }, t.titulo),
        h('td', null, categoria(t)),
        h('td', null, t.prioridad ? badge(t.prioridad, 'p') : ''),
        h('td', null, badge(t.estado, 'e')),
        h('td', null, badge(t.estadoAnalisis, 'a')),
        h('td', null, t.solicitante ? t.solicitante.nombre : ''),
        h('td', { class: 'suave' }, fecha(t.fechaCreacion))));
      resultado.replaceChildren(
        pagina.contenido.length === 0
          ? h('div', { class: 'vacio' }, 'No hay tickets con esos filtros.')
          : h('div', { class: 'tabla-wrap' }, h('table', null,
            h('thead', null, h('tr', null, ['', 'Título', 'Categoría', 'Prioridad', 'Estado', 'Análisis', 'Solicitante', 'Creado'].map((n) => h('th', null, n)))),
            h('tbody', null, filas))),
        h('div', { class: 'paginacion' },
          h('span', { class: 'suave' }, pagina.totalElementos + ' tickets · página ' + (pagina.pagina + 1) + ' de ' + Math.max(1, pagina.totalPaginas)),
          h('button', { disabled: pagina.pagina === 0, onclick: () => { filtros.page--; cargar(); } }, '← Anterior'),
          h('button', { disabled: pagina.pagina + 1 >= pagina.totalPaginas, onclick: () => { filtros.page++; cargar(); } }, 'Siguiente →')));
    } catch (error) {
      aviso(error.message);
    }
  }
  cargar();
}

function describir(e) {
  const d = e.detalle || {};
  if (e.tipo === 'ESTADO_CAMBIADO') return d.de + ' → ' + d.a;
  if (e.tipo === 'TECNICO_ASIGNADO') return 'Técnico #' + d.nuevo + (d.anterior ? ' (antes #' + d.anterior + ')' : '');
  if (e.tipo === 'ANALISIS_COMPLETADO') return [d.categoria, d.prioridad, d.analizadoPor + (d.modelo ? ' · ' + d.modelo : '')].join(' · ');
  if (e.tipo === 'ANALISIS_FALLIDO') return d.error || d.causa || '';
  if (e.tipo === 'TICKET_CREADO') return d.titulo || '';
  return '';
}

async function vistaDetalle(id) {
  const caja = h('div', null, h('div', { class: 'cargando' }, 'Cargando…'));
  contenido().replaceChildren(h('a', { class: 'volver', href: '#/tickets' }, '← Volver a los tickets'), caja);
  let intentos = 0;

  async function cargar() {
    try {
      const t = await api('/api/tickets/' + id);
      const eventos = await api('/api/tickets/' + id + (sesion.rol === 'USUARIO' ? '/historial' : '/linea-tiempo'));
      caja.replaceChildren(h('div', { class: 'detalle' },
        h('div', null,
          h('div', { class: 'tarjeta' },
            h('div', { class: 'cabecera-ticket' }, h('h2', null, t.titulo), badge(t.estado, 'e')),
            h('p', { class: 'suave' }, '#' + t.id + ' · creado ' + fecha(t.fechaCreacion)),
            h('dl', null,
              h('dt', null, 'Descripción'), h('dd', null, t.descripcion),
              h('dt', null, 'Solicitante'), h('dd', null, t.solicitante ? t.solicitante.nombre : ''),
              h('dt', null, 'Técnico'), h('dd', null, t.tecnicoAsignado ? t.tecnicoAsignado.nombre : 'Sin asignar'),
              h('dt', null, 'Actualizado'), h('dd', null, fecha(t.fechaActualizacion)))),
          h('div', { class: 'tarjeta' }, h('h3', null, sesion.rol === 'USUARIO' ? 'Historial de estados' : 'Línea de tiempo'),
            eventos.length === 0 ? h('p', { class: 'suave' }, 'Todavía no hay eventos.') : h('ul', { class: 'linea' }, eventos.map((e) => h('li', null,
              h('div', { class: 'que' }, EVENTOS[e.tipo] || e.tipo),
              h('div', null, describir(e)),
              h('div', { class: 'quien' }, fecha(e.fecha) + ' · ' + (e.actor ? e.actor.rol + ' #' + e.actor.id : 'sistema'))))))),
        h('div', { class: 'tarjeta ia' },
          h('h3', null, 'Análisis de IA', badge(t.estadoAnalisis, 'a')),
          t.estadoAnalisis === 'PENDIENTE' || t.estadoAnalisis === 'ANALIZANDO'
            ? h('p', { class: 'suave' }, 'El análisis se está procesando; esta vista se actualiza sola.')
            : [
              h('div', { class: 'resumen' }, t.resumenIA || 'Sin resumen.'),
              h('dl', null,
                h('dt', null, 'Categoría'), h('dd', null, categoria(t)),
                h('dt', null, 'Prioridad'), h('dd', null, t.prioridad ? badge(t.prioridad, 'p') : '—'),
                h('dt', null, 'Etiquetas'), h('dd', null, t.etiquetasIA.length ? t.etiquetasIA.map((e) => h('span', { class: 'tag' }, e)) : '—'))])));
      // el analisis asincrono tarda unos segundos: se actualiza solo
      if ((t.estadoAnalisis === 'PENDIENTE' || t.estadoAnalisis === 'ANALIZANDO') && intentos++ < 30) {
        temporizador = setTimeout(cargar, 2000);
      }
    } catch (error) {
      caja.replaceChildren(h('div', { class: 'aviso' }, error.message));
    }
  }
  cargar();
}

function vistaNuevo() {
  const titulo = h('input', { required: true, minlength: 5, maxlength: 100, placeholder: 'Ej.: Problema con internet' });
  const descripcion = h('textarea', { required: true, minlength: 20, maxlength: 2000, placeholder: 'Describe el problema con el mayor detalle posible…' });
  const usuarioId = h('input', { type: 'number', required: true, value: sesion.usuarioId });
  if (sesion.rol === 'USUARIO') usuarioId.setAttribute('readonly', '');
  const formulario = h('form', {
    onsubmit: async (e) => {
      e.preventDefault();
      try {
        const t = await api('/api/tickets', { metodo: 'POST', cuerpo: { titulo: titulo.value, descripcion: descripcion.value, usuarioId: Number(usuarioId.value) } });
        location.hash = '#/tickets/' + t.id;
      } catch (error) {
        aviso(error.message);
      }
    },
  }, campo('Título (5-100 caracteres)', titulo), h('br'),
  campo('Descripción (20-2000 caracteres)', descripcion), h('br'), campo('Id del solicitante', usuarioId), h('br'),
  h('button', { class: 'primario' }, 'Crear ticket'));
  contenido().replaceChildren(
    h('h1', { class: 'titulo' }, 'Nuevo ticket'),
    h('p', { class: 'subtitulo' }, 'La IA analiza el ticket después de crearlo; el resultado aparece en el detalle.'),
    h('div', { class: 'tarjeta' }, formulario));
}

async function vistaMetricas() {
  contenido().replaceChildren(h('div', { class: 'cargando' }, 'Cargando…'));
  try {
    const m = await api('/api/tickets/metricas');
    const grupo = (nombre, datos) => {
      const maximo = Math.max(1, ...Object.values(datos));
      return h('div', { class: 'tarjeta' }, h('h3', null, nombre),
        Object.entries(datos).map(([clave, valor]) => {
          const relleno = h('div', { class: 'relleno' });
          relleno.style.width = (valor / maximo * 100) + '%';   // CSSOM: permitido por la CSP
          return h('div', { class: 'barra' }, h('span', null, clave.replace('_', ' ')), h('div', { class: 'carril' }, relleno), h('span', { class: 'valor' }, valor));
        }));
    };
    const kpi = (valor, nombre, color) => h('div', { class: 'kpi ' + (color || '') }, h('div', { class: 'valor' }, valor), h('div', { class: 'nombre' }, nombre));
    contenido().replaceChildren(
      h('h1', { class: 'titulo' }, 'Métricas'),
      h('p', { class: 'subtitulo' }, 'Resumen de los tickets (sin eliminados).'),
      h('div', { class: 'kpis' },
        kpi(m.total, 'Tickets en total'), kpi(m.porEstado.ABIERTO, 'Abiertos', 'azul'),
        kpi(m.porPrioridad.CRITICA, 'Prioridad crítica', 'rojo'), kpi(m.porPrioridad.ALTA, 'Prioridad alta', 'ambar')),
      h('div', { class: 'grid2' }, grupo('Por categoría', m.porCategoria), grupo('Por prioridad', m.porPrioridad),
        grupo('Por estado', m.porEstado), grupo('Por estado de análisis', m.porEstadoAnalisis)));
  } catch (error) {
    contenido().replaceChildren(h('div', { class: 'aviso' }, error.message));
  }
}

// ---------- enrutado
function pintar() {
  clearTimeout(temporizador);
  const [, pagina, id] = location.hash.split('/');
  cabecera(pagina);
  if (!sesion) return vistaLogin();
  if (pagina === 'tickets' && id) return vistaDetalle(id);
  if (pagina === 'nuevo') return vistaNuevo();
  if (pagina === 'metricas' && sesion.rol !== 'USUARIO') return vistaMetricas();
  return vistaListado();
}

window.addEventListener('hashchange', pintar);
pintar();
