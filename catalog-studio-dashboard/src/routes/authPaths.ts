export function featurePath(loggedIn: boolean, workspacePath: string) {
  if (loggedIn) {
    return workspacePath;
  }
  return `/login?next=${encodeURIComponent(workspacePath)}`;
}

export function safeNextPath(value: string | null, fallback = "/dashboard") {
  if (value && value.startsWith("/") && !value.startsWith("//")) {
    return value;
  }
  return fallback;
}
