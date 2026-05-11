export async function copyText(text: string): Promise<boolean> {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return false;
  }

  const canUseClipboard =
    typeof navigator.clipboard?.writeText === 'function' &&
    window.isSecureContext &&
    typeof document !== 'undefined' &&
    document.hasFocus();

  if (!canUseClipboard) {
    return false;
  }

  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

export async function copyTextWithFallback(text: string, label: string): Promise<boolean> {
  const copied = await copyText(text);
  if (copied) {
    return true;
  }

  if (typeof window !== 'undefined' && typeof window.prompt === 'function') {
    window.prompt(`${label} - copy manually`, text);
    return false;
  }

  if (typeof window !== 'undefined' && typeof window.alert === 'function') {
    window.alert(text);
  }

  return false;
}
