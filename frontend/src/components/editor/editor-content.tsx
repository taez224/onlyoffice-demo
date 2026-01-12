'use client';

import { useId, useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { DocumentEditor } from '@onlyoffice/document-editor-react';
import {
  ArrowLeft,
  FileText,
  FileSpreadsheet,
  Presentation,
  ShieldCheck,
  Wifi,
  Clock,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorConfigSuspense } from '@/hooks/use-editor-config';
import { useDocumentEditor } from '@/hooks/use-document-editor';
import { useCurrentTime } from '@/hooks/use-current-time';
import type { DocumentType } from '@/types/document';

interface Props {
  fileKey: string;
}

function getDocumentIcon(documentType: DocumentType) {
  const iconProps = { size: 16 };
  switch (documentType) {
    case 'word':
      return <FileText {...iconProps} className="text-blue-600" />;
    case 'cell':
      return <FileSpreadsheet {...iconProps} className="text-emerald-600" />;
    case 'slide':
      return <Presentation {...iconProps} className="text-orange-600" />;
    default:
      return <FileText {...iconProps} className="text-blue-600" />;
  }
}

function EditorFooter({ currentTime }: { currentTime: string }) {
  return (
    <footer className="h-8 bg-foreground text-background border-t border-border flex items-center justify-between px-4 text-[10px] font-mono select-none z-20">
      <div className="flex items-center gap-6 h-full">
        <div className="flex items-center gap-2 opacity-90">
          <div className="w-2 h-2 rounded-full bg-emerald-500" />
          <span className="uppercase tracking-widest font-bold">CONNECTED</span>
        </div>
        <div className="w-px h-3 bg-background/20" />
        <span className="opacity-60">READ-WRITE</span>
      </div>
      <div className="flex items-center gap-6 h-full">
        <div className="flex items-center gap-2 opacity-60">
          <Wifi size={12} />
          <span>Online</span>
        </div>
        <div className="w-px h-3 bg-background/20" />
        <div className="flex items-center gap-2 opacity-60">
          <ShieldCheck size={12} />
          <span>Secure</span>
        </div>
        <div className="w-px h-3 bg-background/20" />
        <div className="flex items-center gap-2 min-w-[70px] justify-end opacity-90 font-bold">
          <Clock size={12} />
          <span suppressHydrationWarning>{currentTime}</span>
        </div>
      </div>
    </footer>
  );
}

/**
 * 에디터 콘텐츠 컴포넌트
 * Suspense 경계 내에서 사용 - data 항상 존재 보장
 */
export function EditorContent({ fileKey }: Props) {
  const router = useRouter();
  const reactId = useId();
  const editorId = `editor-${fileKey}-${reactId}`;

  const { data: editorConfig } = useEditorConfigSuspense(fileKey);
  const { destroyEditor, onAppReady } = useDocumentEditor({ editorId });
  const currentTime = useCurrentTime();

  const onLoadComponentError = (errorCode: number, errorDescription: string) => {
    console.error('[ONLYOFFICE] Load Error:', errorCode, errorDescription);
  };

  const handleBack = () => {
    destroyEditor();
    router.back();
  };

  const documentServerUrl = useMemo(() => {
    const rawUrl = editorConfig.documentServerUrl;
    return rawUrl.endsWith('/') ? rawUrl.slice(0, -1) : rawUrl;
  }, [editorConfig]);

  const { config } = editorConfig;
  const documentTitle = config.document?.title || 'Untitled';
  const documentType = config.documentType || 'word';

  return (
    <div className="flex flex-col h-screen w-full bg-background overflow-hidden font-sans">
      <header className="h-14 border-b border-border flex items-center justify-between px-0 bg-background z-10 shrink-0">
        <div className="flex items-center h-full">
          <Button
            onClick={handleBack}
            variant="ghost"
            className="h-full w-14 rounded-none border-r border-border hover:bg-muted text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft size={20} />
          </Button>
          <div className="px-6 flex flex-col justify-center h-full">
            <div className="flex items-center gap-3">
              {getDocumentIcon(documentType)}
              <span className="font-bold text-sm tracking-tight text-foreground">
                {documentTitle}
              </span>
            </div>
          </div>
        </div>
      </header>

      <main className="flex-1 relative bg-muted/10">
        <DocumentEditor
          key={editorId}
          id={editorId}
          documentServerUrl={documentServerUrl}
          config={config}
          events_onAppReady={onAppReady}
          onLoadComponentError={onLoadComponentError}
          shardkey={false}
        />
      </main>

      <EditorFooter currentTime={currentTime} />
    </div>
  );
}
