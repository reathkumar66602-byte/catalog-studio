export function syncGenerateEnabled() {
  const btn = document.getElementById("cs-generate") as HTMLButtonElement | null;
  if (!btn) return;
  if (btn.dataset.busy === "1") {
    btn.disabled = true;
    btn.classList.add("busy");
    return;
  }
  btn.classList.remove("busy");
  btn.disabled = false;
}
