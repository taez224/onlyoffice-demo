'use client';

import { Button } from '@/components/ui/button';
import { Download, Trash2, X } from 'lucide-react';

interface BulkActionBarProps {
  selectedCount: number;
  isDeleting: boolean;
  onDelete: () => void;
  onClear: () => void;
}

export function BulkActionBar({
  selectedCount,
  isDeleting,
  onDelete,
  onClear,
}: BulkActionBarProps) {
  if (selectedCount === 0) return null;

  return (
    <div className="fixed bottom-8 left-1/2 -translate-x-1/2 z-50 animate-in slide-in-from-bottom-4 duration-300">
      <div className="bg-foreground text-background shadow-2xl flex items-center p-1.5 rounded-none border border-background/20 gap-1">
        <div className="pl-4 pr-3 text-xs font-mono font-bold border-r border-background/20 flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-secondary block animate-pulse"></span>
          {selectedCount} Selected
        </div>

        <Button
          variant="ghost"
          size="sm"
          className="h-8 text-background hover:bg-background/20 hover:text-background rounded-none text-xs font-medium px-3 disabled:opacity-50 disabled:cursor-not-allowed"
          disabled
        >
          <Download size={14} className="mr-2" />
          Download
        </Button>

        <div className="w-px h-4 bg-background/20 mx-1"></div>

        <Button
          variant="ghost"
          size="sm"
          className="h-8 text-red-400 hover:bg-red-400/20 hover:text-red-300 rounded-none text-xs font-medium px-3"
          onClick={onDelete}
          disabled={isDeleting}
        >
          <Trash2 size={14} className="mr-2" />
          Delete
        </Button>

        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 text-muted-foreground hover:bg-background/20 hover:text-background rounded-none ml-1"
          onClick={onClear}
        >
          <X size={14} />
        </Button>
      </div>
    </div>
  );
}
