// Keep an invalid stored value untouched so a user can still export or inspect it.
// A single damaged preference must not stop the whole app during module startup.
export function readStoredStrings(storage, key, fallback = []) {
  try {
    const value = JSON.parse(storage.getItem(key));
    return Array.isArray(value) ? value.filter(item => typeof item === 'string') : [...fallback];
  } catch {
    return [...fallback];
  }
}

export function readStoredRecord(storage, key) {
  try {
    const value = JSON.parse(storage.getItem(key));
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
  } catch {
    return {};
  }
}
