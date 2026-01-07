'use client';

import { useRef } from 'react';
import { Button } from '@/components/ui/button';
import { PlusIcon, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { validateFile, ALLOWED_EXTENSIONS } from '@/lib/validation';
import { formatFileSize } from '@/lib/format';

interface UploadButtonProps {
  isPending: boolean;
  onUpload: (file: File) => void;
}

export function UploadButton({ isPending, onUpload }: UploadButtonProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const validationError = validateFile(file, formatFileSize);
    if (validationError) {
      toast.error(validationError);
      e.target.value = '';
      return;
    }

    onUpload(file);
    e.target.value = '';
  };

  return (
    <>
      <input
        ref={fileInputRef}
        type="file"
        className="hidden"
        accept={ALLOWED_EXTENSIONS.map((ext) => `.${ext}`).join(',')}
        onChange={handleFileChange}
      />
      <Button
        className="tech-btn h-10 rounded-none"
        onClick={handleClick}
        disabled={isPending}
      >
        {isPending ? (
          <Loader2 size={16} className="animate-spin" />
        ) : (
          <PlusIcon size={16} />
        )}
        {isPending ? 'Uploading...' : 'Upload New'}
      </Button>
    </>
  );
}
