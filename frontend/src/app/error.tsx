'use client';

import { useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { AlertCircle } from 'lucide-react';

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center p-8 max-w-md">
        <div className="w-16 h-16 mx-auto mb-6 bg-red-50 dark:bg-red-900/20 rounded-full flex items-center justify-center">
          <AlertCircle size={28} className="text-red-600" />
        </div>
        <h2 className="text-lg font-bold text-foreground mb-2">오류가 발생했습니다</h2>
        <p className="text-sm text-muted-foreground mb-4">
          페이지를 불러오는 중 문제가 발생했습니다.
        </p>
        <Button onClick={reset} variant="outline">
          다시 시도
        </Button>
      </div>
    </div>
  );
}
