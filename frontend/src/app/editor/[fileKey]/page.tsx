'use client';

import { Suspense } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { ArrowLeft, Loader2, AlertCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EditorContent, EditorErrorBoundary } from '@/components/editor';
import { isValidUUID } from '@/lib/validation';

function LoadingState() {
  const router = useRouter();

  return (
    <div className="flex flex-col h-screen w-full bg-background overflow-hidden font-sans">
      <header className="h-14 border-b border-border flex items-center px-4 bg-background z-10 shrink-0">
        <Button
          onClick={() => router.back()}
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

function InvalidFileKeyState() {
  const router = useRouter();

  return (
    <div className="flex flex-col h-screen w-full bg-background overflow-hidden font-sans">
      <header className="h-14 border-b border-border flex items-center px-4 bg-background z-10 shrink-0">
        <Button
          onClick={() => router.back()}
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
          <h2 className="text-lg font-bold text-foreground mb-2">잘못된 문서 식별자</h2>
          <p className="text-sm text-muted-foreground mb-4">
            유효하지 않은 fileKey 형식입니다.
          </p>
          <Button onClick={() => router.push('/')} variant="outline">
            문서 목록으로 돌아가기
          </Button>
        </div>
      </main>
    </div>
  );
}

/**
 * 에디터 페이지
 *
 * Suspense 패턴 적용:
 * - 유효성 검사는 Suspense 전에 처리
 * - EditorErrorBoundary: 쿼리 에러 처리
 * - Suspense: 로딩 상태 처리
 * - EditorContent: 성공 상태만 처리 (data 항상 존재)
 */
export default function EditorPage() {
  const params = useParams();
  const fileKey = params.fileKey as string;

  // 유효성 검사: Suspense 전에 처리
  if (!isValidUUID(fileKey)) {
    return <InvalidFileKeyState />;
  }

  return (
    <EditorErrorBoundary>
      <Suspense fallback={<LoadingState />}>
        <EditorContent fileKey={fileKey} />
      </Suspense>
    </EditorErrorBoundary>
  );
}