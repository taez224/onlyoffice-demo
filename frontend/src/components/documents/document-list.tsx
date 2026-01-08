'use client';

import type { SortingState, OnChangeFn } from '@tanstack/react-table';
import { DocumentTable } from './document-table';
import type { DocumentResponse } from '@/types/document';

interface DocumentListProps {
  documents: DocumentResponse[];
  selectedIds: string[];
  sorting: SortingState;
  onSortingChange: OnChangeFn<SortingState>;
  onToggleSelection: (fileKey: string) => void;
  onToggleAll: () => void;
}

export function DocumentList({
  documents,
  selectedIds,
  sorting,
  onSortingChange,
  onToggleSelection,
  onToggleAll,
}: DocumentListProps) {
  return (
    <DocumentTable
      documents={documents}
      selectedIds={selectedIds}
      sorting={sorting}
      onSortingChange={onSortingChange}
      onToggleSelection={onToggleSelection}
      onToggleAll={onToggleAll}
    />
  );
}
