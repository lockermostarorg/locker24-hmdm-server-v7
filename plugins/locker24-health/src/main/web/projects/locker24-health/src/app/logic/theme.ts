export function onThemeChange(callback: () => void): () => void {
  const observer = new MutationObserver(() => callback());
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme'],
  });
  const media = window.matchMedia?.('(prefers-color-scheme: dark)');
  const onMedia = () => callback();
  media?.addEventListener('change', onMedia);
  return () => {
    observer.disconnect();
    media?.removeEventListener('change', onMedia);
  };
}
