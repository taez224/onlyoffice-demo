import './App.css';
import Editor from './components/Editor'

function App() {
  // Get fileKey from URL query parameter
  const queryParams = new URLSearchParams(window.location.search);
  const fileKey = queryParams.get('fileKey');

  if (!fileKey) {
    return (
      <div style={{ padding: '20px', textAlign: 'center' }}>
        <h2>Error: Missing fileKey parameter</h2>
        <p>Please provide a fileKey in the URL query string.</p>
        <p>Example: <code>?fileKey=550e8400-e29b-41d4-a716-446655440000</code></p>
      </div>
    );
  }

  return (
    <>
      <Editor fileKey={fileKey} />
    </>
  )
}

export default App
