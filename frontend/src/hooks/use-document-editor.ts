import { useRef, useEffect, useCallback } from 'react';

interface DocEditorInstance {
  destroyEditor: () => void;
}

interface UseDocumentEditorOptions {
  editorId: string;
}

export function useDocumentEditor({ editorId }: UseDocumentEditorOptions) {
  const editorRef = useRef<DocEditorInstance | null>(null);
  const isInitializedRef = useRef(false);

  const destroyEditor = useCallback(() => {
    if (!isInitializedRef.current || !editorRef.current) return;
    
    try {
      editorRef.current.destroyEditor();
    } catch {
      /* empty */
    }
    editorRef.current = null;
    isInitializedRef.current = false;
  }, []);

  useEffect(() => {
    return () => {
      destroyEditor();
    };
  }, [destroyEditor]);

  const onAppReady = useCallback(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const docEditor = (window as any).DocEditor;
    if (docEditor?.instances?.[editorId]) {
      editorRef.current = docEditor.instances[editorId];
      isInitializedRef.current = true;
    }
  }, [editorId]);

  return {
    destroyEditor,
    onAppReady,
  };
}
