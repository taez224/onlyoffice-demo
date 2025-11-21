import './App.css';
import Editor from './components/Editor'

function App() {
  // For demo, we hardcode the filename or get it from URL params
  const queryParams = new URLSearchParams(window.location.search);
  const fileName = queryParams.get('fileName') || 'test.docx';

  return (
    <>
      <Editor fileName={fileName} />
    </>
  )
}

export default App
