/* Catalog Studio keep-alive: only while fill is running. Restores native timers after. */
(function () {
  "use strict";
  if ((window as Window & { __csAwake?: boolean }).__csAwake) return;
  (window as Window & { __csAwake?: boolean }).__csAwake = true;

  function loginPath() {
    try {
      return /(^|\/)login(\/|$)/i.test(location.pathname);
    } catch {
      return false;
    }
  }
  if (loginPath()) {
    const wait = setInterval(function () {
      if (loginPath()) return;
      clearInterval(wait);
      boot();
    }, 1000);
    return;
  }
  boot();

  function boot() {
    const origRAF = window.requestAnimationFrame;
    const origCAF = window.cancelAnimationFrame;
    const origST = window.setTimeout;
    const origCT = window.clearTimeout;
    const origHasFocus = document.hasFocus;
    if (!origRAF) return;
    const realRAF = origRAF.bind(window);
    const realCAF = origCAF.bind(window);
    const realST = origST.bind(window);
    const realCT = origCT.bind(window);
    const realHasFocus = origHasFocus.bind(document);
    const dHidden = Object.getOwnPropertyDescriptor(Document.prototype, "hidden");
    const dVis = Object.getOwnPropertyDescriptor(Document.prototype, "visibilityState");
    let active = false;
    const rafs = new Map();
    let rafId = 1;

    function patch() {
      if (active) return;
      active = true;
      try {
        Object.defineProperty(document, "hidden", { configurable: true, get: function () { return false; } });
        Object.defineProperty(document, "visibilityState", { configurable: true, get: function () { return "visible"; } });
        document.hasFocus = function () { return true; };
      } catch {
        // ignore
      }
      window.requestAnimationFrame = function (cb) {
        const id = rafId++;
        rafs.set(id, cb);
        return id;
      };
      window.cancelAnimationFrame = function (id) {
        rafs.delete(id);
      };
      window.setTimeout = function (cb, ms) {
        if (typeof cb === "function" && (!ms || ms < 1000)) {
          const id = rafId++;
          rafs.set(id, cb);
          return id;
        }
        return realST.call(window, cb, ms);
      };
    }

    function unpatch() {
      if (!active) return;
      active = false;
      rafs.clear();
      window.requestAnimationFrame = origRAF;
      window.cancelAnimationFrame = origCAF;
      window.setTimeout = origST;
      window.clearTimeout = origCT;
      document.hasFocus = origHasFocus;
      try {
        if (dHidden) Object.defineProperty(document, "hidden", dHidden);
        if (dVis) Object.defineProperty(document, "visibilityState", dVis);
      } catch {
        // ignore
      }
    }

    window.addEventListener("message", function (event) {
      if (event.source !== window || event.origin !== location.origin || !event.data) return;
      if (event.data.type === "CS_AWAKE") {
        if (event.data.on) patch();
        else unpatch();
        return;
      }
      if (event.data.type === "CS_TICK" && active) {
        const cbs = [...rafs.values()];
        rafs.clear();
        cbs.forEach(function (cb) {
          try {
            cb(performance.now());
          } catch {
            // ignore
          }
        });
      }
    });
    void realCAF;
    void realRAF;
    void realCT;
    void realHasFocus;
    void dHidden;
  }
})();
