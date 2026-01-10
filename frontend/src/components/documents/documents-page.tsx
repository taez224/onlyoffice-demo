'use client';

import { Suspense, useState } from 'react';
import type { SortingState } from '@tanstack/react-table';
import { useUploadDocument } from '@/hooks/use-upload-document';
import { useDeleteDocuments } from '@/hooks/use-delete-documents';
import { DocumentsErrorBoundary } from './documents-error-boundary';
import { DocumentList } from './document-list';
import { TableSkeleton } from './table-skeleton';
import { BulkActionBar } from './bulk-action-bar';
import { DeleteConfirmDialog } from './delete-confirm-dialog';
import { UploadButton } from './upload-button';

export function DocumentsPage() {
  const [selectedFileKeys, setSelectedFileKeys] = useState<string[]>([]);
  const [sorting, setSorting] = useState<SortingState>([]);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const uploadMutation = useUploadDocument();
  const deleteDocumentsMutation = useDeleteDocuments();

  const toggleSelection = (fileKey: string) => {
    setSelectedFileKeys((prev) =>
      prev.includes(fileKey) ? prev.filter((id) => id !== fileKey) : [...prev, fileKey]
    );
  };



  const handleDeleteClick = () => {
    if (selectedFileKeys.length === 0) return;
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = () => {
    deleteDocumentsMutation.mutate(selectedFileKeys);
    setSelectedFileKeys([]);
    setDeleteDialogOpen(false);
  };

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
          <DocumentsErrorBoundary>
            <Suspense fallback={<TableSkeleton />}>
              <DocumentList
                selectedFileKeys={selectedFileKeys}
                sorting={sorting}
                onSortingChange={setSorting}
                onToggleSelection={toggleSelection}
                setSelectedFileKeys={setSelectedFileKeys}
              />
            </Suspense>
          </DocumentsErrorBoundary>
        </div>
      </div>

      <BulkActionBar
        selectedCount={selectedFileKeys.length}
        isDeleting={deleteDocumentsMutation.isPending}
        onDelete={handleDeleteClick}
        onClear={() => setSelectedFileKeys([])}
      />

      <DeleteConfirmDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        selectedCount={selectedFileKeys.length}
        onConfirm={handleDeleteConfirm}
      />
    </div>
  );
}
