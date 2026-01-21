'use client';

import { useCallback } from 'react';
import type { SortingState, OnChangeFn } from '@tanstack/react-table';
import { useDocumentsSuspense } from '@/hooks/use-documents';
import { DocumentTable } from './document-table';

interface DocumentListProps {
  selectedFileKeys: string[];
  sorting: SortingState;
  onSortingChange: OnChangeFn<SortingState>;
  onToggleSelection: (fileKey: string) => void;
  setSelectedFileKeys: React.Dispatch<React.SetStateAction<string[]>>;
}

export function DocumentList({
  selectedFileKeys,
  sorting,
  onSortingChange,
  onToggleSelection,
  setSelectedFileKeys,
}: DocumentListProps) {
  const { data: documents } = useDocumentsSuspense();

  const toggleAll = useCallback(() => {
    setSelectedFileKeys((prev) =>
      prev.length === documents.length ? [] : documents.map((d) => d.fileKey)
    );
  }, [documents, setSelectedFileKeys]);

  return (
    <DocumentTable
      documents={documents}
      selectedFileKeys={selectedFileKeys}
      sorting={sorting}
      onSortingChange={onSortingChange}
      onToggleSelection={onToggleSelection}
      onToggleAll={toggleAll}
    />
  );
}
