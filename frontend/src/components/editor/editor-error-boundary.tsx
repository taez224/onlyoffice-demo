'use client';

import { QueryErrorResetBoundary } from '@tanstack/react-query';
import { ErrorBoundary } from 'react-error-boundary';
import { AlertCircle, RefreshCw, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useRouter } from 'next/navigation';

interface Props {
  children: React.ReactNode;
}

/**
 * 에디터 전용 에러 경계
 * QueryErrorResetBoundary와 통합하여 쿼리 에러 시 재시도 가능
 */
export function EditorErrorBoundary({ children }: Props) {
  const router = useRouter();

  return (
    <QueryErrorResetBoundary>
      {({ reset }) => (
        <ErrorBoundary
          onReset={reset}
          fallbackRender={({ resetErrorBoundary }) => (
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
                  <h2 className="text-lg font-bold text-foreground mb-2">
                    문서를 불러올 수 없습니다
                  </h2>
                  <p className="text-sm text-muted-foreground mb-6">
                    문서가 존재하지 않거나 접근 권한이 없습니다.
                  </p>
                  <div className="flex gap-3 justify-center">
                    <Button onClick={resetErrorBoundary} variant="outline">
                      <RefreshCw size={16} className="mr-2" />
                      다시 시도
                    </Button>
                    <Button onClick={() => router.push('/')} variant="default">
                      문서 목록으로
                    </Button>
                  </div>
                </div>
              </main>
            </div>
          )}
        >
          {children}
        </ErrorBoundary>
      )}
    </QueryErrorResetBoundary>
  );
}
