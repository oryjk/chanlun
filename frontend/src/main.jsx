import { createRoot } from 'react-dom/client'
import { useCases } from './app/container.js'
import App from './App.jsx'
import './styles.css'

createRoot(document.getElementById('root')).render(<App useCases={useCases} />)
