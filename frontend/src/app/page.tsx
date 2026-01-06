'use client';

import { useState } from 'react';
import type { SortingState } from '@tanstack/react-table';
import { useDocuments } from '@/hooks/use-documents';
import { useUploadDocument } from '@/hooks/use-upload-document';
import { useDeleteDocuments } from '@/hooks/use-delete-documents';
import {
  DocumentTable,
  BulkActionBar,
  DeleteConfirmDialog,
  UploadButton,
} from '@/components/documents';

export default function HomePage() {
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const { data: documents = [], isLoading, error } = useDocuments();
  const uploadMutation = useUploadDocument();
  const deleteDocumentsMutation = useDeleteDocuments();

  const toggleSelection = (fileKey: string) => {
    setSelectedIds((prev) =>
      prev.includes(fileKey) ? prev.filter((id) => id !== fileKey) : [...prev, fileKey]
    );
  };

  const toggleAll = () => {
    if (selectedIds.length === documents.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(documents.map((d) => d.fileKey));
    }
  };

  const handleDeleteClick = () => {
    if (selectedIds.length === 0) return;
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = () => {
    deleteDocumentsMutation.mutate(selectedIds);
    setSelectedIds([]);
    setDeleteDialogOpen(false);
  };

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <h2 className="text-xl font-bold text-destructive mb-2">오류 발생</h2>
          <p className="text-muted-foreground">문서 목록을 불러올 수 없습니다.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen p-6 md:p-12 max-w-7xl mx-auto flex flex-col relative">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-8 shrink-0">
        <div>
          <h1 className="text-3xl md:text-4xl font-black tracking-tight mb-2">Documents</h1>
          <p className="text-muted-foreground max-w-lg leading-relaxed">
            Manage your secure documents.
          </p>
        </div>

        <div className="flex gap-3">
          <UploadButton
            isPending={uploadMutation.isPending}
            onUpload={(file) => uploadMutation.mutate(file)}
          />
        </div>
      </div>

      <div className="flex-1 flex flex-col">
        <div className="tech-border bg-card flex-1 flex flex-col overflow-x-auto relative">
          <DocumentTable
            documents={documents}
            isLoading={isLoading}
            selectedIds={selectedIds}
            sorting={sorting}
            onSortingChange={setSorting}
            onToggleSelection={toggleSelection}
            onToggleAll={toggleAll}
          />
        </div>
      </div>

      <BulkActionBar
        selectedCount={selectedIds.length}
        isDeleting={deleteDocumentsMutation.isPending}
        onDelete={handleDeleteClick}
        onClear={() => setSelectedIds([])}
      />

      <DeleteConfirmDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        selectedCount={selectedIds.length}
        onConfirm={handleDeleteConfirm}
      />
    </div>
  );
}
