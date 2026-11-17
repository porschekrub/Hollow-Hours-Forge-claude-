let listener;
export function onProgress(fn) {
    listener = fn;
}
export function report(h, ev, id, n = 1) {
    try {
        listener?.(h, ev, id, n);
    }
    catch {
        /* ignore */
    }
}
