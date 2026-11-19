// Shown by the dashboards while their data loads from the server, or if loading fails.
export default function DataGate({ error, onRetry }) {
  if (error) {
    return (
      <div className="grid min-h-screen place-items-center bg-canvas p-6 text-center" role="alert">
        <div className="grid max-w-sm gap-4">
          <p className="text-lg font-extrabold">Couldn’t load your data</p>
          <p className="text-ink-soft">{error}</p>
          <button
            type="button"
            onClick={onRetry}
            className="justify-self-center rounded-full bg-primary px-5 py-2.5 font-bold text-white transition hover:brightness-110"
          >
            Try again
          </button>
        </div>
      </div>
    )
  }
  return (
    <div className="grid min-h-screen place-items-center bg-canvas" role="status" aria-label="Loading">
      <span className="size-9 animate-spin rounded-full border-4 border-primary-soft border-t-primary" />
    </div>
  )
}
