const state = {
  orgs: [],
  org: localStorage.getItem("kova.pwa.org") || "UllensakerRKH",
  events: [],
  view: "all",
  search: "",
  favorites: new Set(JSON.parse(localStorage.getItem("kova.pwa.favorites") || "[]")),
  selected: null,
};

const $ = (id) => document.getElementById(id);
const orgSelect = $("orgSelect");
const eventsEl = $("events");
const emptyEl = $("empty");
const statusText = $("statusText");
const searchInput = $("searchInput");
const template = $("eventTemplate");

function isIOS() {
  return /iphone|ipad|ipod/i.test(navigator.userAgent);
}
function isStandalone() {
  return window.matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;
}
function eventKey(event) {
  return state.org + "|" + event.id;
}
function displayTime(value = "") {
  const t = value.trim();
  if (!t) return "Tid ikke oppgitt";
  if (t.startsWith("->")) return "Til " + (t.match(/\d{1,2}:\d{2}/)?.[0] || t);
  return t;
}
function saveFavorites() {
  localStorage.setItem("kova.pwa.favorites", JSON.stringify([...state.favorites]));
}
function dateInRange(dateIso, days) {
  const date = new Date(dateIso + "T00:00:00");
  const today = new Date();
  today.setHours(0,0,0,0);
  const end = new Date(today);
  end.setDate(end.getDate() + days);
  return date >= today && date <= end;
}
function filteredEvents() {
  const q = state.search.trim().toLowerCase();
  return state.events.filter(event => {
    if (state.view === "favorites" && !state.favorites.has(eventKey(event))) return false;
    if (state.view === "week" && !dateInRange(event.dateIso, 7)) return false;
    if (state.view === "month" && !dateInRange(event.dateIso, 30)) return false;
    if (state.view === "all" && event.dateIso && !dateInRange(event.dateIso, 3650)) return false;
    return !q || [event.description,event.type,event.dateLabel,event.time]
      .some(v => (v || "").toLowerCase().includes(q));
  });
}
function render() {
  eventsEl.innerHTML = "";
  const list = filteredEvents();
  $("eventCount").textContent = list.length;
  emptyEl.classList.toggle("hidden", list.length !== 0);

  for (const event of list) {
    const node = template.content.cloneNode(true);
    node.querySelector(".type").textContent = event.type || "Aktivitet";
    node.querySelector(".description").textContent = event.description || "KOVA-aktivitet";
    node.querySelector(".meta").textContent = [event.dateLabel, displayTime(event.time)].filter(Boolean).join(" • ");
    const fav = node.querySelector(".favorite");
    fav.textContent = state.favorites.has(eventKey(event)) ? "★" : "☆";
    fav.onclick = () => {
      const key = eventKey(event);
      state.favorites.has(key) ? state.favorites.delete(key) : state.favorites.add(key);
      saveFavorites();
      render();
    };
    node.querySelector(".event-main").onclick = () => openDetail(event);
    eventsEl.appendChild(node);
  }
}
function openDetail(event) {
  state.selected = event;
  $("detailType").textContent = event.type || "Aktivitet";
  $("detailTitle").textContent = event.description || "KOVA-aktivitet";
  $("detailMeta").textContent = [event.dateLabel, displayTime(event.time)].filter(Boolean).join(" • ");
  $("detailKovaLink").href = event.sourceUrl || "https://www.kova.no/";
  updateDialogFavorite();
  $("detailDialog").showModal();
}
function updateDialogFavorite() {
  if (!state.selected) return;
  $("favoriteDialogBtn").textContent = state.favorites.has(eventKey(state.selected)) ? "★ Fjern favoritt" : "☆ Legg til favoritt";
}
async function loadOrganizations() {
  const response = await fetch("./data/organizations.json", {cache:"no-store"});
  if (!response.ok) throw new Error("Kunne ikke hente korpslisten");
  const payload = await response.json();
  state.orgs = payload.organizations.filter(o => o.category === "hjelpekorps");
  orgSelect.innerHTML = state.orgs.map(o =>
    `<option value="${escapeHtml(o.code)}">${escapeHtml(o.name)}</option>`
  ).join("");
  if (!state.orgs.some(o => o.code === state.org)) state.org = "UllensakerRKH";
  orgSelect.value = state.org;
}
async function loadEvents() {
  statusText.textContent = "Oppdaterer…";
  try {
    const response = await fetch(`./data/${encodeURIComponent(state.org)}.json`, {cache:"no-store"});
    if (!response.ok) throw new Error("Korpsdata er ikke tilgjengelig ennå");
    const payload = await response.json();
    state.events = payload.events || [];
    $("orgTitle").textContent = payload.organization?.name || state.org;
    statusText.textContent = "KOVA-data oppdatert " + formatUpdated(payload.updatedAt);
    render();
  } catch (error) {
    statusText.textContent = error.message || "Kunne ikke hente KOVA";
    state.events = [];
    render();
  }
}
function formatUpdated(value) {
  if (!value) return "";
  try { return new Intl.DateTimeFormat("nb-NO",{dateStyle:"short",timeStyle:"short"}).format(new Date(value)); }
  catch { return value; }
}
function escapeHtml(value="") {
  return value.replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"}[c]));
}

orgSelect.addEventListener("change", async () => {
  state.org = orgSelect.value;
  localStorage.setItem("kova.pwa.org", state.org);
  await loadEvents();
});
searchInput.addEventListener("input", () => { state.search = searchInput.value; render(); });
$("refreshBtn").onclick = loadEvents;
$("viewTabs").addEventListener("click", (event) => {
  const btn = event.target.closest("button[data-view]");
  if (!btn) return;
  state.view = btn.dataset.view;
  document.querySelectorAll("#viewTabs button").forEach(b => b.classList.toggle("active", b === btn));
  render();
});
$("closeDialog").onclick = () => $("detailDialog").close();
$("favoriteDialogBtn").onclick = () => {
  if (!state.selected) return;
  const key = eventKey(state.selected);
  state.favorites.has(key) ? state.favorites.delete(key) : state.favorites.add(key);
  saveFavorites();
  updateDialogFavorite();
  render();
};

let installPrompt = null;
window.addEventListener("beforeinstallprompt", event => {
  event.preventDefault();
  installPrompt = event;
  $("installBtn").classList.remove("hidden");
});
$("installBtn").onclick = async () => {
  if (!installPrompt) return;
  await installPrompt.prompt();
  installPrompt = null;
  $("installBtn").classList.add("hidden");
};
if (isIOS() && !isStandalone()) $("installHint").classList.remove("hidden");

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("./sw.js").catch(() => {});
}

(async () => {
  try {
    await loadOrganizations();
    await loadEvents();
  } catch (error) {
    statusText.textContent = error.message || "Oppstart feilet";
  }
})();
