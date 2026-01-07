import { Loader2 } from 'lucide-react';

export default function EditorLoading() {
  return (
    <div className="flex flex-col h-screen w-full bg-background overflow-hidden font-sans">
      <header className="h-14 border-b border-border flex items-center px-4 bg-background z-10 shrink-0">
        <div className="h-10 w-20 bg-muted animate-pulse rounded" />
      </header>
      <main className="flex-1 flex items-center justify-center bg-muted/10">
        <div className="text-center p-8 max-w-md animate-in zoom-in-95 duration-500 fade-in">
          <div className="relative w-16 h-16 mx-auto mb-6">
            <div className="absolute inset-0 bg-blue-100 dark:bg-blue-900/20 rounded-full animate-ping opacity-20" />
            <div className="relative w-16 h-16 bg-blue-50 dark:bg-blue-900/20 rounded-full flex items-center justify-center border border-blue-100 dark:border-blue-800">
              <Loader2 size={28} className="text-blue-600 animate-spin" />
            </div>
          </div>
          <h2 className="text-lg font-bold text-foreground mb-2">Loading Editor...</h2>
          <p className="text-sm text-muted-foreground">Initializing document environment</p>
        </div>
      </main>
    </div>
  );
}
