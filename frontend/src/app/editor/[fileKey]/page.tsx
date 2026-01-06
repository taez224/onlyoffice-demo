'use client';

import { useState, useMemo } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { DocumentEditor } from '@onlyoffice/document-editor-react';
import {
  ArrowLeft,
  Loader2,
  FileText,
  FileSpreadsheet,
  Presentation,
  ShieldCheck,
  Wifi,
  Clock,
  AlertCircle,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorConfig } from '@/hooks/use-editor-config';
import { useDocumentEditor } from '@/hooks/use-document-editor';
import { useCurrentTime } from '@/hooks/use-current-time';
import type { DocumentType } from '@/types/document';

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

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

function ErrorState({ onBack, onGoHome }: { onBack: () => void; onGoHome: () => void }) {
  return (
    <div className="flex flex-col h-screen w-full bg-background overflow-hidden font-sans">
      <header className="h-14 border-b border-border flex items-center px-4 bg-background z-10 shrink-0">
        <Button
          onClick={onBack}
          variant="ghost"
          className="h-10 rounded-none hover:bg-muted text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft size={20} className="mr-2" />
          Back
        </Button>
      </header>
      <main className="flex-1 flex items-center justify-center">
        <div className="text-center p-8 max-w-md">
          <div className="w-16 h-16 mx-auto mb-6 bg-red-50 dark:bg-red-900/20 rounded-full flex items-center justify-center">
            <AlertCircle size={28} className="text-red-600" />
          </div>
          <h2 className="text-lg font-bold text-foreground mb-2">문서를 불러올 수 없습니다</h2>
          <p className="text-sm text-muted-foreground mb-4">
            문서가 존재하지 않거나 접근 권한이 없습니다.
          </p>
          <Button onClick={onGoHome} variant="outline">
            문서 목록으로 돌아가기
          </Button>
        </div>
      </main>
    </div>
  );
}

function LoadingState({ onBack }: { onBack: () => void }) {
  return (
    <div className="flex flex-col h-screen w-full bg-background overflow-hidden font-sans">
      <header className="h-14 border-b border-border flex items-center px-4 bg-background z-10 shrink-0">
        <Button
          onClick={onBack}
          variant="ghost"
          className="h-10 rounded-none hover:bg-muted text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft size={20} className="mr-2" />
          Back
        </Button>
      </header>
      <main className="flex-1 flex items-center justify-center bg-muted/10">
        <div className="text-center p-8 max-w-md animate-in zoom-in-95 duration-500 fade-in">
          <div className="relative w-16 h-16 mx-auto mb-6">
            <div className="absolute inset-0 bg-blue-100 dark:bg-blue-900/20 rounded-full animate-ping opacity-20" />
            <div className="relative w-16 h-16 bg-blue-50 dark:bg-blue-900/20 rounded-full flex items-center justify-center border border-blue-100 dark:border-blue-800">
              <Loader2 size={28} className="text-blue-600 animate-spin" />
            </div>
          </div>
          <h2 className="text-lg font-bold text-foreground mb-2">Loading Editor...</h2>
          <p className="text-sm text-muted-foreground">Initializing document environment</p>
        </div>
      </main>
    </div>
  );
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

export default function EditorPage() {
  const params = useParams();
  const router = useRouter();
  const fileKey = params.fileKey as string;
  const isValidFileKey = UUID_REGEX.test(fileKey);

  const [sessionTimestamp] = useState(() => Date.now());
  const editorId = `editor-${fileKey}-${sessionTimestamp}`;

  const { data: editorConfig, isLoading, error } = useEditorConfig(isValidFileKey ? fileKey : '');
  const { destroyEditor, onAppReady } = useDocumentEditor({ editorId });
  const currentTime = useCurrentTime();

  const onLoadComponentError = (errorCode: number, errorDescription: string) => {
    console.error('[ONLYOFFICE] Load Error:', errorCode, errorDescription);
  };

  const handleBack = () => {
    destroyEditor();
    router.back();
  };

  const handleGoHome = () => {
    destroyEditor();
    router.push('/');
  };

  const documentServerUrl = useMemo(() => {
    if (!editorConfig) return '';
    const rawUrl = editorConfig.documentServerUrl;
    return rawUrl.endsWith('/') ? rawUrl.slice(0, -1) : rawUrl;
  }, [editorConfig]);

  if (!isValidFileKey || error) {
    return <ErrorState onBack={handleBack} onGoHome={handleGoHome} />;
  }

  if (isLoading || !editorConfig) {
    return <LoadingState onBack={handleBack} />;
  }

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
