export function ErrorState({ message }: { message: string }) {
  return (
    <div className="bt-alert bt-alert-error" role="alert">
      {message}
    </div>
  )
}
