import React, { useEffect, useState } from 'react';
import { DocumentEditor } from '@onlyoffice/document-editor-react';
import axios from 'axios';

interface EditorProps {
    fileName: string;
}

const Editor: React.FC<EditorProps> = ({ fileName }) => {
    const [config, setConfig] = useState<any>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchConfig = async () => {
            try {
                const response = await axios.get(`/api/config?fileName=${fileName}`);
                setConfig(response.data);
            } catch (error) {
                console.error("Failed to fetch editor config", error);
            } finally {
                setLoading(false);
            }
        };

        fetchConfig();
    }, [fileName]);

    if (loading) {
        return <div>Loading Editor...</div>;
    }

    if (!config) {
        return <div>Error loading configuration</div>;
    }

    return (
        <div style={{ height: '100vh', width: '100%' }}>
            <DocumentEditor
                id="docxEditor"
                documentServerUrl={config.documentServerUrl}
                config={config.config}
                events_onDocumentReady={() => console.log("Document Ready")}
            />
        </div>
    );
};

export default Editor;
