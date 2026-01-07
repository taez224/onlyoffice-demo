'use client';

import { QueryErrorResetBoundary } from '@tanstack/react-query';
import { ErrorBoundary } from 'react-error-boundary';
import { Button } from '@/components/ui/button';
import { AlertCircle, RefreshCw } from 'lucide-react';

interface Props {
  children: React.ReactNode;
}

export function DocumentsErrorBoundary({ children }: Props) {
  return (
    <QueryErrorResetBoundary>
      {({ reset }) => (
        <ErrorBoundary
          onReset={reset}
          fallbackRender={({ resetErrorBoundary }) => (
            <div className="flex-1 flex items-center justify-center py-20">
              <div className="text-center">
                <AlertCircle size={48} className="mx-auto text-destructive mb-4" />
                <h2 className="text-lg font-bold mb-2">문서를 불러올 수 없습니다</h2>
                <p className="text-muted-foreground mb-4">
                  네트워크 오류가 발생했습니다.
                </p>
                <Button onClick={resetErrorBoundary} variant="outline">
                  <RefreshCw size={16} className="mr-2" />
                  다시 시도
                </Button>
              </div>
            </div>
          )}
        >
          {children}
        </ErrorBoundary>
      )}
    </QueryErrorResetBoundary>
  );
}
