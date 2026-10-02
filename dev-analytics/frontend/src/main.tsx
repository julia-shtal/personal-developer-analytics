import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Latin + Latin Extended only — the UI ships English copy; other Fontsource
// subsets (Cyrillic, Vietnamese, …) would bundle fonts no page ever renders.
import '@fontsource/geist/latin-400.css'
import '@fontsource/geist/latin-500.css'
import '@fontsource/geist/latin-600.css'
import '@fontsource/geist/latin-ext-400.css'
import '@fontsource/geist/latin-ext-500.css'
import '@fontsource/geist/latin-ext-600.css'
import '@fontsource/geist-mono/latin-400.css'
import '@fontsource/geist-mono/latin-500.css'
import '@fontsource/geist-mono/latin-600.css'
import '@fontsource/geist-mono/latin-ext-400.css'
import '@fontsource/geist-mono/latin-ext-500.css'
import '@fontsource/geist-mono/latin-ext-600.css'
import '@fontsource/ibm-plex-mono/latin-400.css'
import '@fontsource/ibm-plex-mono/latin-400-italic.css'
import '@fontsource/ibm-plex-mono/latin-500.css'
import '@fontsource/ibm-plex-mono/latin-500-italic.css'
import '@fontsource/ibm-plex-mono/latin-600.css'
import '@fontsource/ibm-plex-mono/latin-ext-400.css'
import '@fontsource/ibm-plex-mono/latin-ext-400-italic.css'
import '@fontsource/ibm-plex-mono/latin-ext-500.css'
import '@fontsource/ibm-plex-mono/latin-ext-500-italic.css'
import '@fontsource/ibm-plex-mono/latin-ext-600.css'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
