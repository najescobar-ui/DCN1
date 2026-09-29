import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { BrowserRouter } from 'react-router'
import App from './App'
import { userManager } from './auth/oidc'
import { CartProvider } from './cart/CartContext'
import './index.css'

/** Removes ?code=&state= from the URL once the callback has been processed. */
const onSigninCallback = () => window.history.replaceState({}, document.title, window.location.pathname)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider userManager={userManager} onSigninCallback={onSigninCallback}>
      <CartProvider>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </CartProvider>
    </AuthProvider>
  </StrictMode>,
)
