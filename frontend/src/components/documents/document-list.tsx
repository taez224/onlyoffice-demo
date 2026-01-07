'use client';

import type { SortingState, OnChangeFn } from '@tanstack/react-table';
import { useDocumentsSuspense } from '@/hooks/use-documents';
import { DocumentTable } from './document-table';

interface DocumentListProps {
  selectedIds: string[];
  sorting: SortingState;
  onSortingChange: OnChangeFn<SortingState>;
  onToggleSelection: (fileKey: string) => void;
  onToggleAll: () => void;
}

export function DocumentList({
  selectedIds,
  sorting,
  onSortingChange,
  onToggleSelection,
  onToggleAll,
}: DocumentListProps) {
  const { data: documents } = useDocumentsSuspense();

  return (
    <DocumentTable
      documents={documents}
      isLoading={false}
      selectedIds={selectedIds}
      sorting={sorting}
      onSortingChange={onSortingChange}
      onToggleSelection={onToggleSelection}
      onToggleAll={onToggleAll}
    />
  );
}
