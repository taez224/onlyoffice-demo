'use client';

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

  const toggleAll = () => {
    if (selectedFileKeys.length === documents.length) {
      setSelectedFileKeys([]);
    } else {
      setSelectedFileKeys(documents.map((d) => d.fileKey));
    }
  };

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
