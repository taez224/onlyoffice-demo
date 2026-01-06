'use client';

import { useRef, useState } from 'react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  PlusIcon,
  SearchIcon,
  FilterIcon,
  MoreHorizontal,
  FileText,
  FileSpreadsheet,
  Presentation,
  FileType,
  File,
  Download,
  Trash2,
  Share2,
  X,
  Loader2,
} from 'lucide-react';
import { toast } from 'sonner';
import { useDocuments } from '@/hooks/use-documents';
import { useUploadDocument } from '@/hooks/use-upload-document';
import { useDeleteDocuments } from '@/hooks/use-delete-documents';
import type { DocumentResponse } from '@/types/document';

const MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
const ALLOWED_EXTENSIONS = ['docx', 'xlsx', 'pptx', 'pdf'];

function validateFile(file: File): string | null {
  if (file.size > MAX_FILE_SIZE) {
    return `파일 크기가 100MB를 초과합니다 (현재: ${formatFileSize(file.size)})`;
  }

  const extension = file.name.split('.').pop()?.toLowerCase();
  if (!extension || !ALLOWED_EXTENSIONS.includes(extension)) {
    return `허용되지 않은 파일 형식입니다. 허용: ${ALLOWED_EXTENSIONS.join(', ')}`;
  }

  return null;
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(k)), sizes.length - 1);
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
}

function formatDateTime(isoString: string): string {
  return new Date(isoString).toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function getFileIcon(fileType: string) {
  const type = fileType.toUpperCase();
  switch (type) {
    case 'DOCX':
    case 'DOC':
      return <FileText className="text-blue-600 dark:text-blue-400" size={18} />;
    case 'XLSX':
    case 'XLS':
      return <FileSpreadsheet className="text-emerald-600 dark:text-emerald-400" size={18} />;
    case 'PPTX':
    case 'PPT':
      return <Presentation className="text-orange-600 dark:text-orange-400" size={18} />;
    case 'PDF':
      return <FileType className="text-red-600 dark:text-red-400" size={18} />;
    default:
      return <File className="text-muted-foreground" size={18} />;
  }
}

function getTypeBadgeClass(fileType: string) {
  const type = fileType.toUpperCase();
  switch (type) {
    case 'DOCX':
    case 'DOC':
      return 'bg-blue-100 dark:bg-blue-900/20';
    case 'XLSX':
    case 'XLS':
      return 'bg-emerald-100 dark:bg-emerald-900/20';
    case 'PPTX':
    case 'PPT':
      return 'bg-orange-100 dark:bg-orange-900/20';
    case 'PDF':
      return 'bg-red-100 dark:bg-red-900/20';
    default:
      return 'bg-muted';
  }
}

export default function HomePage() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const { data: documents = [], isLoading, error } = useDocuments();
  const uploadMutation = useUploadDocument();
  const deleteDocumentsMutation = useDeleteDocuments();

  const handleUploadClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const validationError = validateFile(file);
    if (validationError) {
      toast.error(validationError);
      e.target.value = '';
      return;
    }

    uploadMutation.mutate(file);
    e.target.value = '';
  };

  const handleDeleteSelected = () => {
    if (selectedIds.length === 0) return;
    deleteDocumentsMutation.mutate(selectedIds);
    setSelectedIds([]);
  };

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
      <input
        ref={fileInputRef}
        type="file"
        className="hidden"
        accept=".docx,.xlsx,.pptx,.pdf"
        onChange={handleFileChange}
      />

      <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-8 shrink-0">
        <div>
          <h1 className="text-3xl md:text-4xl font-black tracking-tight mb-2">Documents</h1>
          <p className="text-muted-foreground max-w-lg leading-relaxed">
            Manage your secure documents.
          </p>
        </div>

        <div className="flex gap-3">
          <Button
            variant="outline"
            className="h-10 border-border bg-background hover:bg-muted rounded-none border"
          >
            <FilterIcon size={16} className="mr-2" /> Filter
          </Button>
          <Button
            className="tech-btn h-10 rounded-none"
            onClick={handleUploadClick}
            disabled={uploadMutation.isPending}
          >
            {uploadMutation.isPending ? (
              <Loader2 size={16} className="animate-spin" />
            ) : (
              <PlusIcon size={16} />
            )}
            {uploadMutation.isPending ? 'Uploading...' : 'Upload New'}
          </Button>
        </div>
      </div>

      <div className="space-y-6 flex-1 flex flex-col">
        <div className="relative shrink-0">
          <SearchIcon
            className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground"
            size={16}
          />
          <input
            type="text"
            placeholder="Search by filename or ID..."
            className="w-full h-12 pl-10 pr-4 bg-background border border-border focus:outline-none focus:border-primary transition-colors text-sm"
          />
        </div>

        <div className="tech-border bg-card flex-1 flex flex-col overflow-hidden relative">
          {isLoading ? (
            <div className="flex-1 flex items-center justify-center">
              <Loader2 size={32} className="animate-spin text-muted-foreground" />
            </div>
          ) : (
            <div className="shrink-0">
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent border-border bg-muted/30">
                    <TableHead className="w-[40px] text-center border-r border-border align-middle p-0">
                      <div className="flex items-center justify-center w-full h-full">
                        <input
                          type="checkbox"
                          className="accent-primary w-4 h-4 cursor-pointer"
                          checked={selectedIds.length === documents.length && documents.length > 0}
                          onChange={toggleAll}
                        />
                      </div>
                    </TableHead>
                    <TableHead className="w-[50px] text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">
                      ID
                    </TableHead>
                    <TableHead className="w-[40%] text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">
                      Name
                    </TableHead>
                    <TableHead className="text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">
                      Size
                    </TableHead>
                    <TableHead className="text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">
                      Created
                    </TableHead>
                    <TableHead className="text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center border-r border-border align-middle">
                      Modified
                    </TableHead>
                    <TableHead className="w-[80px] text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center align-middle">
                      Action
                    </TableHead>
                  </TableRow>
                </TableHeader>
                {documents.length > 0 && (
                  <TableBody>
                    {documents.map((doc: DocumentResponse) => (
                      <TableRow
                        key={doc.fileKey}
                        className={`border-border hover:bg-muted/50 transition-colors cursor-pointer group h-14 ${
                          selectedIds.includes(doc.fileKey) ? 'bg-muted/40' : ''
                        }`}
                        onClick={() => toggleSelection(doc.fileKey)}
                      >
                        <TableCell
                          className="text-center border-r border-border p-0"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <div
                            className="flex items-center justify-center h-full w-full py-3"
                            onClick={() => toggleSelection(doc.fileKey)}
                          >
                            <input
                              type="checkbox"
                              className="accent-primary w-4 h-4 cursor-pointer"
                              checked={selectedIds.includes(doc.fileKey)}
                              onChange={() => {}}
                              onClick={(e) => {
                                e.stopPropagation();
                                toggleSelection(doc.fileKey);
                              }}
                            />
                          </div>
                        </TableCell>
                        <TableCell className="text-center text-muted-foreground border-r border-border font-mono text-xs">
                          {doc.id}
                        </TableCell>
                        <TableCell className="font-medium text-foreground border-r border-border pl-4">
                          <div className="flex items-center gap-3">
                            <Link
                              href={`/editor/${doc.fileKey}`}
                              onClick={(e) => e.stopPropagation()}
                              className={`w-8 h-8 flex items-center justify-center rounded-full ${getTypeBadgeClass(
                                doc.fileType
                              )} shrink-0 transition-transform duration-300 group-hover:scale-110 group-hover:shadow-sm`}
                            >
                              {getFileIcon(doc.fileType)}
                            </Link>
                            <Link
                              href={`/editor/${doc.fileKey}`}
                              onClick={(e) => e.stopPropagation()}
                              className="transition-transform duration-300 group-hover:translate-x-1 truncate select-none hover:underline underline-offset-4 decoration-primary/30"
                            >
                              {doc.fileName}
                            </Link>
                          </div>
                        </TableCell>
                        <TableCell className="text-center text-muted-foreground border-r border-border font-mono text-xs select-none">
                          {formatFileSize(doc.fileSize)}
                        </TableCell>
                        <TableCell className="text-center text-muted-foreground border-r border-border font-mono text-xs select-none">
                          {formatDateTime(doc.createdAt)}
                        </TableCell>
                        <TableCell className="text-center text-muted-foreground border-r border-border font-mono text-xs select-none">
                          {formatDateTime(doc.updatedAt)}
                        </TableCell>
                        <TableCell className="text-center">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8 hover:bg-background rounded-none"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <MoreHorizontal size={16} className="text-muted-foreground" />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                )}
              </Table>
              {documents.length === 0 && !isLoading && (
                <div className="flex-1 flex items-center justify-center py-20">
                  <div className="text-center">
                    <FileText size={48} className="mx-auto text-muted-foreground/50 mb-4" />
                    <p className="text-muted-foreground">문서가 없습니다. 새 문서를 업로드하세요.</p>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {selectedIds.length > 0 && (
        <div className="fixed bottom-8 left-1/2 -translate-x-1/2 z-50 animate-in slide-in-from-bottom-4 duration-300">
          <div className="bg-foreground text-background shadow-2xl flex items-center p-1.5 rounded-none border border-background/20 gap-1">
            <div className="pl-4 pr-3 text-xs font-mono font-bold border-r border-background/20 flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-secondary block animate-pulse"></span>
              {selectedIds.length} Selected
            </div>

            <Button
              variant="ghost"
              size="sm"
              className="h-8 text-background hover:bg-background/20 hover:text-background rounded-none text-xs font-medium px-3"
            >
              <Download size={14} className="mr-2" />
              Download
            </Button>

            <Button
              variant="ghost"
              size="sm"
              className="h-8 text-background hover:bg-background/20 hover:text-background rounded-none text-xs font-medium px-3"
            >
              <Share2 size={14} className="mr-2" />
              Share
            </Button>

            <div className="w-px h-4 bg-background/20 mx-1"></div>

            <Button
              variant="ghost"
              size="sm"
              className="h-8 text-red-400 hover:bg-red-400/20 hover:text-red-300 rounded-none text-xs font-medium px-3"
              onClick={handleDeleteSelected}
              disabled={deleteDocumentsMutation.isPending}
            >
              <Trash2 size={14} className="mr-2" />
              Delete
            </Button>

            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-muted-foreground hover:bg-background/20 hover:text-background rounded-none ml-1"
              onClick={() => setSelectedIds([])}
            >
              <X size={14} />
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
