import React from 'react'
import { createRoot } from 'react-dom/client'
import { useCases } from './app/container.js'
import App from './App.jsx'
import './styles.css'

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App useCases={useCases} />
  </React.StrictMode>,
)
