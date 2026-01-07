'use client';

export function TableSkeleton() {
  return (
    <div className="min-w-max">
      <div className="h-10 bg-muted/30 border-b border-border flex items-center px-4 gap-4">
        <div className="w-4 h-4 bg-muted rounded animate-pulse" />
        <div className="w-8 h-3 bg-muted rounded animate-pulse" />
        <div className="w-16 h-3 bg-muted rounded animate-pulse" />
        <div className="flex-1 h-3 bg-muted rounded animate-pulse max-w-[100px]" />
        <div className="w-12 h-3 bg-muted rounded animate-pulse" />
        <div className="w-20 h-3 bg-muted rounded animate-pulse" />
        <div className="w-20 h-3 bg-muted rounded animate-pulse" />
        <div className="w-12 h-3 bg-muted rounded animate-pulse" />
      </div>

      {[...Array(5)].map((_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 px-4 h-14 border-b border-border"
          style={{ animationDelay: `${i * 100}ms` }}
        >
          <div className="w-4 h-4 bg-muted rounded animate-pulse" />
          <div className="w-8 h-4 bg-muted rounded animate-pulse" />
          <div className="w-16 h-4 bg-muted rounded animate-pulse" />
          <div className="flex items-center gap-3 flex-1">
            <div className="w-8 h-8 bg-muted rounded-full animate-pulse" />
            <div className="w-40 h-4 bg-muted rounded animate-pulse" />
          </div>
          <div className="w-14 h-4 bg-muted rounded animate-pulse" />
          <div className="w-28 h-4 bg-muted rounded animate-pulse" />
          <div className="w-28 h-4 bg-muted rounded animate-pulse" />
          <div className="w-8 h-8 bg-muted rounded animate-pulse" />
        </div>
      ))}
    </div>
  );
}
