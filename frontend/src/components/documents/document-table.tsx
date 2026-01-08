'use client';

import Link from 'next/link';
import {
  useReactTable,
  getCoreRowModel,
  getSortedRowModel,
  flexRender,
  type ColumnDef,
  type SortingState,
  type Row,
  type OnChangeFn,
} from '@tanstack/react-table';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import {
  MoreHorizontal,
  FileText,
  FileSpreadsheet,
  Presentation,
  FileType,
  File,
  ArrowUpDown,
  ArrowUp,
  ArrowDown,
} from 'lucide-react';
import { formatFileSize, formatDateTime } from '@/lib/format';
import type { DocumentResponse } from '@/types/document';

const HEADER_STYLES: Record<string, string> = {
  select: 'w-[50px] min-w-[50px] p-0',
  id: 'w-[50px]',
  fileKey: 'w-[120px]',
  fileName: 'w-[35%] text-left pl-4',
};

const CELL_STYLES: Record<string, string> = {
  select: 'w-[50px] min-w-[50px] text-center p-0',
  id: 'text-center text-muted-foreground font-mono text-xs',
  fileKey: 'text-center text-muted-foreground font-mono text-xs',
  fileName: 'font-medium text-foreground pl-4',
  fileSize: 'text-center text-muted-foreground font-mono text-xs select-none',
  createdAt: 'text-center text-muted-foreground font-mono text-xs select-none',
  updatedAt: 'text-center text-muted-foreground font-mono text-xs select-none',
  actions: 'text-center',
};

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

function SortableHeader({
  column,
  children,
}: {
  column: { getIsSorted: () => false | 'asc' | 'desc'; toggleSorting: (desc?: boolean) => void };
  children: React.ReactNode;
}) {
  const sorted = column.getIsSorted();
  return (
    <button
      className="flex items-center gap-1 hover:text-foreground transition-colors"
      onClick={() => column.toggleSorting(sorted === 'asc')}
    >
      {children}
      {sorted === 'asc' ? (
        <ArrowUp size={14} />
      ) : sorted === 'desc' ? (
        <ArrowDown size={14} />
      ) : (
        <ArrowUpDown size={14} className="opacity-50" />
      )}
    </button>
  );
}

function SelectCheckbox({
  row,
  selectedIds,
  onToggle,
}: {
  row: Row<DocumentResponse>;
  selectedIds: string[];
  onToggle: (fileKey: string) => void;
}) {
  return (
    <div
      className="flex items-center justify-center h-full w-full py-3"
      onClick={(e) => {
        e.stopPropagation();
        onToggle(row.original.fileKey);
      }}
    >
      <input
        type="checkbox"
        className="accent-primary w-4 h-4 cursor-pointer"
        checked={selectedIds.includes(row.original.fileKey)}
        onChange={() => onToggle(row.original.fileKey)}
        onClick={(e) => e.stopPropagation()}
      />
    </div>
  );
}

function SelectAllCheckbox({
  documents,
  selectedIds,
  onToggleAll,
}: {
  documents: DocumentResponse[];
  selectedIds: string[];
  onToggleAll: () => void;
}) {
  return (
    <div className="flex items-center justify-center w-full h-full">
      <input
        type="checkbox"
        className="accent-primary w-4 h-4 cursor-pointer"
        checked={selectedIds.length === documents.length && documents.length > 0}
        onChange={onToggleAll}
      />
    </div>
  );
}

const columns: ColumnDef<DocumentResponse>[] = [
  {
    id: 'select',
    header: () => null,
    cell: () => null,
    enableSorting: false,
  },
  {
    accessorKey: 'id',
    header: 'ID',
    cell: ({ row }) => row.original.id,
    enableSorting: false,
  },
  {
    accessorKey: 'fileKey',
    header: 'FileKey',
    cell: ({ row }) => (
      <span title={row.original.fileKey}>{row.original.fileKey.slice(0, 8)}...</span>
    ),
    enableSorting: false,
  },
  {
    accessorKey: 'fileName',
    header: ({ column }) => <SortableHeader column={column}>Name</SortableHeader>,
    cell: ({ row }) => (
      <div className="flex items-center gap-3">
        <Link
          href={`/editor/${row.original.fileKey}`}
          onClick={(e) => e.stopPropagation()}
          className={`w-8 h-8 flex items-center justify-center rounded-full ${getTypeBadgeClass(
            row.original.fileType
          )} shrink-0 transition-transform duration-300 group-hover:scale-110 group-hover:shadow-sm`}
        >
          {getFileIcon(row.original.fileType)}
        </Link>
        <Link
          href={`/editor/${row.original.fileKey}`}
          onClick={(e) => e.stopPropagation()}
          className="transition-transform duration-300 group-hover:translate-x-1 truncate select-none hover:underline underline-offset-4 decoration-primary/30"
        >
          {row.original.fileName}
        </Link>
      </div>
    ),
  },
  {
    accessorKey: 'fileSize',
    header: ({ column }) => <SortableHeader column={column}>Size</SortableHeader>,
    cell: ({ row }) => formatFileSize(row.original.fileSize),
  },
  {
    accessorKey: 'createdAt',
    header: ({ column }) => <SortableHeader column={column}>Created</SortableHeader>,
    cell: ({ row }) => formatDateTime(row.original.createdAt),
  },
  {
    accessorKey: 'updatedAt',
    header: ({ column }) => <SortableHeader column={column}>Modified</SortableHeader>,
    cell: ({ row }) => formatDateTime(row.original.updatedAt),
  },
  {
    id: 'actions',
    header: 'Action',
    cell: () => (
      <Button
        variant="ghost"
        size="icon"
        className="h-8 w-8 hover:bg-background rounded-none"
        onClick={(e) => e.stopPropagation()}
      >
        <MoreHorizontal size={16} className="text-muted-foreground" />
      </Button>
    ),
    enableSorting: false,
  },
];

interface DocumentTableProps {
  documents: DocumentResponse[];
  selectedIds: string[];
  sorting: SortingState;
  onSortingChange: OnChangeFn<SortingState>;
  onToggleSelection: (fileKey: string) => void;
  onToggleAll: () => void;
}

export function DocumentTable({
  documents,
  selectedIds,
  sorting,
  onSortingChange,
  onToggleSelection,
  onToggleAll,
}: DocumentTableProps) {
  const table = useReactTable({
    data: documents,
    columns,
    state: { sorting },
    onSortingChange,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  return (
    <div className="min-w-max">
      <Table>
        <TableHeader>
          {table.getHeaderGroups().map((headerGroup) => (
            <TableRow
              key={headerGroup.id}
              className="hover:bg-transparent border-border bg-muted/30"
            >
              {headerGroup.headers.map((header, idx) => {
                const isLast = idx === headerGroup.headers.length - 1;
                const baseStyle =
                  'text-xs font-bold uppercase tracking-wider text-muted-foreground h-10 text-center align-middle';
                const columnStyle = HEADER_STYLES[header.id] ?? '';
                const borderStyle = isLast ? '' : 'border-r border-border';

                return (
                  <TableHead
                    key={header.id}
                    className={`${baseStyle} ${columnStyle} ${borderStyle}`}
                  >
                    {header.id === 'select' ? (
                      <SelectAllCheckbox
                        documents={documents}
                        selectedIds={selectedIds}
                        onToggleAll={onToggleAll}
                      />
                    ) : header.isPlaceholder ? null : (
                      flexRender(header.column.columnDef.header, header.getContext())
                    )}
                  </TableHead>
                );
              })}
            </TableRow>
          ))}
        </TableHeader>
        {table.getRowModel().rows.length > 0 && (
          <TableBody>
            {table.getRowModel().rows.map((row) => (
              <TableRow
                key={row.id}
                className={`border-border hover:bg-muted/50 transition-colors cursor-pointer group h-14 ${
                  selectedIds.includes(row.original.fileKey) ? 'bg-muted/40' : ''
                }`}
                onClick={() => onToggleSelection(row.original.fileKey)}
              >
                {row.getVisibleCells().map((cell, idx) => {
                  const isLast = idx === row.getVisibleCells().length - 1;
                  const columnStyle = CELL_STYLES[cell.column.id] ?? '';
                  const borderStyle = isLast ? '' : 'border-r border-border';

                  return (
                    <TableCell
                      key={cell.id}
                      className={`${columnStyle} ${borderStyle}`}
                      onClick={
                        cell.column.id === 'select'
                          ? (e) => e.stopPropagation()
                          : undefined
                      }
                    >
                      {cell.column.id === 'select' ? (
                        <SelectCheckbox
                          row={row}
                          selectedIds={selectedIds}
                          onToggle={onToggleSelection}
                        />
                      ) : (
                        flexRender(cell.column.columnDef.cell, cell.getContext())
                      )}
                    </TableCell>
                  );
                })}
              </TableRow>
            ))}
          </TableBody>
        )}
      </Table>
      {documents.length === 0 && (
        <div className="flex-1 flex items-center justify-center py-20">
          <div className="text-center">
            <FileText size={48} className="mx-auto text-muted-foreground/50 mb-4" />
            <p className="text-muted-foreground">문서가 없습니다. 새 문서를 업로드하세요.</p>
          </div>
        </div>
      )}
    </div>
  );
}
